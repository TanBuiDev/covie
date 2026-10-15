package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

/**
 * Promotional discounts apply only to Seller Billing invoices, never buyer orders.
 * The voucher row is locked during invoice reservation to serialize quota checks.
 */
@Service
public class BillingVoucherService {
    private final JdbcTemplate jdbc;
    private final AppClock clock;
    private final AdminAccessPolicy admins;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;

    public BillingVoucherService(JdbcTemplate jdbc,AppClock clock,AdminAccessPolicy admins,
            AuditLogWriter audit,RequestIdAccessor requestIds){
        this.jdbc=jdbc;this.clock=clock;this.admins=admins;this.audit=audit;this.requestIds=requestIds;
    }

    public static String normalize(String raw){
        if(raw==null||raw.isBlank())return null;
        String code=raw.strip().toUpperCase(java.util.Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]{4,32}"))throw invalid("Invalid subscription promotion code.");
        return code;
    }

    /**
     * The caller must hold a transaction and merchant lock already. No external
     * payOS call or HTTP activity occurs while reserving the promotion.
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public Discount reserve(String rawCode,String planCode,UUID merchantId,long originalAmount){
        String code=normalize(rawCode);
        if(code==null)return new Discount(null,0L,originalAmount);
        var rows=jdbc.query("""
            select code,plan_code,discount_type,discount_value,
                   max_redemptions,per_merchant_limit,starts_at,ends_at,enabled
            from billing_vouchers where code=? for update
            """,this::map,code);
        if(rows.isEmpty())throw invalid("Subscription promotion is unavailable.");
        Voucher offer=rows.getFirst();
        validateOffer(offer,planCode,originalAmount,clock.now());
        checkQuotas(offer,merchantId);
        long discount=discountAmount(originalAmount,offer.discountType(),offer.discountValue());
        return new Discount(code,discount,originalAmount-discount);
    }

    /**
     * Called after invoice insertion, while the same transaction still holds
     * the voucher FOR UPDATE lock from reserve(). The FK enforces invoice existence.
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public void attachReservation(UUID invoiceId,UUID merchantId,long originalAmount,Discount discount){
        if(discount.code()==null)return;
        if(discount.discountVnd()<=0||discount.amountDueVnd()!=originalAmount-discount.discountVnd())
            throw invalid("Invalid reserved promotion.");
        jdbc.update("""
            insert into billing_voucher_redemptions
              (invoice_id,voucher_code,merchant_id,original_amount_vnd,discount_vnd,created_at)
            values (?,?,?,?,?,?)
            """,invoiceId,discount.code(),merchantId,originalAmount,discount.discountVnd(),
            Timestamp.from(clock.now()));
    }

    @Transactional(readOnly=true)
    public Discount quote(String rawCode,String planCode,UUID merchantId,long originalAmount){
        String code=normalize(rawCode);
        if(code==null)return new Discount(null,0L,originalAmount);
        var rows=jdbc.query("""
            select code,plan_code,discount_type,discount_value,
                   max_redemptions,per_merchant_limit,starts_at,ends_at,enabled
            from billing_vouchers where code=?
            """,this::map,code);
        if(rows.isEmpty())throw invalid("Subscription promotion is unavailable.");
        Voucher offer=rows.getFirst();
        validateOffer(offer,planCode,originalAmount,clock.now());
        checkQuotas(offer,merchantId);
        long discount=discountAmount(originalAmount,offer.discountType(),offer.discountValue());
        return new Discount(code,discount,originalAmount-discount);
    }

    private void checkQuotas(Voucher offer,UUID merchantId){
        // VOID/REFUNDED are excluded. PENDING_PAYMENT holds capacity, even if
        // local link TTL expired; only authenticated payOS finality can VOID it.
        var counts=jdbc.query("""
            select count(*) as total,
                   count(*) filter(where r.merchant_id=?) as merchant_count
            from billing_voucher_redemptions r
            join billing_invoices i on i.id=r.invoice_id
            where r.voucher_code=? and i.status in ('PENDING_PAYMENT','PAID')
            """,rs->{
                rs.next();
                return new long[]{rs.getLong("total"),rs.getLong("merchant_count")};
            },merchantId,offer.code());
        if(counts[0]>=offer.maxRedemptions()||counts[1]>=offer.perMerchantLimit())
            throw invalid("Subscription promotion redemption limit reached.");
    }

    private static void validateOffer(Voucher offer,String planCode,long amount,Instant now){
        if(amount<=1 || !offer.enabled() || now.isBefore(offer.startsAt()) || !now.isBefore(offer.endsAt())
                || (offer.planCode()!=null&&!offer.planCode().equals(planCode)))
            throw invalid("Subscription promotion is not valid for this plan and date.");
    }

    /** FIXED VND or percent in basis points (1–9999 = 0.01%–99.99%). */
    public static long discountAmount(long price,String type,long value){
        if(price<=1)throw invalid("Invalid price for a discounted checkout.");
        long result;
        if("FIXED".equals(type)){
            result=value;
        }else if("PERCENT".equals(type)&&value>0&&value<10000){
            result=Math.multiplyExact(price,value)/10000;
        }else throw invalid("Invalid discount type or value.");
        if(result<=0||result>=price)throw invalid("Promotion would produce invalid amount.");
        return result;
    }

    @Transactional
    public Voucher create(CreateRequest request){
        var actor=admins.requireAdmin();
        String code=normalize(request==null?null:request.code());
        if(code==null||request.discountType()==null
                ||!List.of("FIXED","PERCENT").contains(request.discountType())
                ||request.discountValue()<=0
                ||("PERCENT".equals(request.discountType())&&request.discountValue()>=10000)
                ||request.discountValue()>1000000000L
                ||request.maxRedemptions()<1||request.maxRedemptions()>100000
                ||request.perMerchantLimit()<1||request.perMerchantLimit()>100
                ||request.startsAt()==null||request.endsAt()==null
                ||!request.endsAt().isAfter(request.startsAt())
                ||!request.endsAt().isAfter(clock.now())
                ||request.planCode()!=null&&!request.planCode().matches("[A-Z0-9_]{1,60}"))
            throw invalid("Invalid Seller subscription promotion.");
        Integer planExists=request.planCode()==null?1:jdbc.queryForObject(
            "select count(*) from billing_plans where code=? and retired_at is null",
            Integer.class,request.planCode());
        if(planExists==null||planExists!=1)throw invalid("Unknown or retired plan.");
        try{
            jdbc.update("""
                insert into billing_vouchers
                  (code,plan_code,discount_type,discount_value,max_redemptions,per_merchant_limit,
                   starts_at,ends_at,enabled,created_by_user_id,created_at)
                values (?,?,?,?,?,?,?,?,true,?,?)
                """,code,request.planCode(),request.discountType(),request.discountValue(),
                request.maxRedemptions(),request.perMerchantLimit(),
                Timestamp.from(request.startsAt()),Timestamp.from(request.endsAt()),
                actor.userId(),Timestamp.from(clock.now()));
        }catch(org.springframework.dao.DuplicateKeyException ex){
            throw new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.CONFLICT,
                "Subscription promotion code already exists.");
        }
        audit.securityEvent(actor.userId(),"BILLING_VOUCHER_CREATED","BILLING_VOUCHER",null,
            requestIds.current(),Map.of("code",code,"maxRedemptions",request.maxRedemptions()));
        return find(code);
    }

    @Transactional(readOnly=true)
    public List<Voucher> list(){
        admins.requireAdmin();
        return jdbc.query("""
            select code,plan_code,discount_type,discount_value,max_redemptions,per_merchant_limit,
                   starts_at,ends_at,enabled from billing_vouchers order by created_at desc limit 100
            """,this::map);
    }

    @Transactional
    public void disable(String raw){
        var actor=admins.requireAdmin();
        String code=normalize(raw);
        int changed=jdbc.update("""
            update billing_vouchers set enabled=false,disabled_by_user_id=?,disabled_at=?
            where code=? and enabled=true
            """,actor.userId(),Timestamp.from(clock.now()),code);
        if(changed!=1)throw invalid("Subscription promotion is already disabled or unknown.");
        audit.securityEvent(actor.userId(),"BILLING_VOUCHER_DISABLED","BILLING_VOUCHER",null,
            requestIds.current(),Map.of("code",code));
    }

    private Voucher find(String code){
        return jdbc.query("""
            select code,plan_code,discount_type,discount_value,max_redemptions,per_merchant_limit,
                   starts_at,ends_at,enabled from billing_vouchers where code=?
            """,this::map,code).getFirst();
    }

    private Voucher map(java.sql.ResultSet rs,int index)throws java.sql.SQLException{
        return new Voucher(rs.getString("code"),rs.getString("plan_code"),rs.getString("discount_type"),
            rs.getLong("discount_value"),rs.getInt("max_redemptions"),
            rs.getInt("per_merchant_limit"),rs.getTimestamp("starts_at").toInstant(),
            rs.getTimestamp("ends_at").toInstant(),rs.getBoolean("enabled"));
    }

    private static ApiException invalid(String message){
        return new ApiException(ErrorCode.BILLING_PRICE_INVALID,HttpStatus.UNPROCESSABLE_ENTITY,message);
    }

    public record Voucher(String code,String planCode,String discountType,long discountValue,
        int maxRedemptions,int perMerchantLimit,Instant startsAt,Instant endsAt,boolean enabled){}
    public record Discount(String code,long discountVnd,long amountDueVnd){}
    public record CreateRequest(String code,String planCode,String discountType,long discountValue,
        int maxRedemptions,int perMerchantLimit,Instant startsAt,Instant endsAt){}
}
