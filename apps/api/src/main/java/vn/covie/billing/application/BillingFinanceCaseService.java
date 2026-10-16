package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

/**
 * Stores finance claims and verified *manual* refund completion.
 * No provider payout action is issued; confirm requires independent bank/provider proof.
 */
@Service
public class BillingFinanceCaseService {
    private final JdbcTemplate jdbc;
    private final BillingCheckoutService checkout;
    private final AdminAccessPolicy admins;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final OutboxEventWriter outbox;
    private final boolean refundApproval;
    private BillingFinanceAccessPolicy finance;
    @Autowired public void setFinanceAccess(BillingFinanceAccessPolicy finance){this.finance=finance;}

    public BillingFinanceCaseService(JdbcTemplate jdbc,BillingCheckoutService checkout,
            AdminAccessPolicy admins,AppClock clock,AuditLogWriter audit,
            RequestIdAccessor requestIds,OutboxEventWriter outbox,
            @Value("${COVIE_BILLING_REFUND_CONFIRM_APPROVED:false}") boolean refundApproval){
        this.jdbc=jdbc;this.checkout=checkout;this.admins=admins;this.clock=clock;
        this.audit=audit;this.requestIds=requestIds;this.outbox=outbox;this.refundApproval=refundApproval;
    }

    @Transactional
    public Case create(UUID merchantId,UUID invoiceId,Request request) {
        checkout.requireOwner(merchantId);
        if(request==null||request.kind()==null||!List.of("REFUND","DISPUTE").contains(request.kind())
                ||request.reason()==null||request.reason().isBlank()
                ||request.reason().length()>2000)throw invalid("Invalid finance case.");
        var found=jdbc.query("""
            select status from billing_invoices where id=? and merchant_id=? for update
            """,(rs,n)->rs.getString(1),invoiceId,merchantId);
        if(found.isEmpty())throw notFound();
        if(!"PAID".equals(found.getFirst()))throw conflict("Only paid invoices can be disputed.");
        var previous=jdbc.query("""
            select id from billing_finance_cases where invoice_id=? and kind=?
            """,(rs,n)->(UUID)rs.getObject(1),invoiceId,request.kind());
        if(!previous.isEmpty()) return details(previous.getFirst());
        UUID owner=checkout.currentOwnerId();
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into billing_finance_cases
              (id,invoice_id,merchant_id,kind,status,reason,created_by_user_id,created_at)
            values (?,?,?,?,'REQUESTED',?,?,?)
            """,id,invoiceId,merchantId,request.kind(),request.reason().trim(),owner,
            Timestamp.from(clock.now()));
        outbox.append(new OutboxEventIntent("BILLING_FINANCE_CASE_REQUESTED","BILLING_FINANCE_CASE",
            id,Map.of("merchantId",merchantId.toString(),"invoiceId",invoiceId.toString(),"kind",request.kind())));
        return details(id);
    }

    @Transactional(readOnly=true)
    public List<Case> list(String status,int limit){
        if(finance!=null)finance.requireReader();else admins.requireAdmin();
        if(limit<1||limit>100)throw invalid("Page limit must be between 1 and 100.");
        if(status!=null&&!List.of("REQUESTED","REVIEWING","REJECTED","CONFIRMED").contains(status))throw invalid("Invalid case status.");
        String sql="select * from billing_finance_cases"+
            (status==null?"":" where status=?")+" order by created_at desc,id desc limit ?";
        return status==null?jdbc.query(sql,this::map,limit):jdbc.query(sql,this::map,status,limit);
    }

    @Transactional
    public Case resolve(UUID caseId,Resolution request) {
        var actor=admins.requireAdmin();
        if(finance!=null){
            if(request!=null && "CONFIRMED".equals(request.status()))finance.requireApprover();
            else finance.requireOperator();
        }
        if(request==null||request.status()==null
                ||!List.of("REVIEWING","REJECTED","CONFIRMED").contains(request.status())
                ||request.note()==null||request.note().isBlank()||request.note().length()>2000)
            throw invalid("Invalid resolution.");
        var rows=jdbc.query("select * from billing_finance_cases where id=? for update",this::map,caseId);
        if(rows.isEmpty())throw notFound();
        Case current=rows.getFirst();
        if(current.status().equals("CONFIRMED")||current.status().equals("REJECTED")) {
            throw conflict("This case is already resolved.");
        }
        if("REVIEWING".equals(request.status())) {
            UUID requester=jdbc.queryForObject("select created_by_user_id from billing_finance_cases where id=?",
                UUID.class,caseId);
            if(actor.userId().equals(requester)) throw conflict("A requester cannot review their own refund.");
        }
        if("CONFIRMED".equals(request.status())) {
            if(!refundApproval)throw conflict("Refund confirmation requires separate operator approval.");
            var reviewers=jdbc.query("""
                select created_by_user_id,reviewed_by_user_id
                from billing_finance_cases where id=?
                """,(rs,n)->new UUID[]{(UUID)rs.getObject(1),(UUID)rs.getObject(2)},caseId);
            requireIndependentRefundApproval(current.status(), actor.userId(),
                reviewers.getFirst()[0], reviewers.getFirst()[1]);
            if(!"REFUND".equals(current.kind()))throw conflict("Disputes require investigation before refund.");
            if(request.externalReference()==null||!request.externalReference().matches("[A-Za-z0-9._/-]{6,180}"))
                throw invalid("Externally confirmed refund reference required.");
            var invoice=jdbc.query("""
                select status from billing_invoices where id=? for update
                """,(rs,n)->rs.getString(1),current.invoiceId());
            if(invoice.isEmpty()||!"PAID".equals(invoice.getFirst()))throw conflict("Invoice is not PAID.");
            jdbc.update("update billing_invoices set status='REFUNDED',version=version+1 where id=?",current.invoiceId());
            jdbc.update("""
                update billing_payment_attempts set status='REFUNDED',version=version+1
                where invoice_id=? and status='SUCCEEDED'
                """,current.invoiceId());
            jdbc.update("""
                update billing_entitlement_grants set revoked_at=?
                where source_type='PAID_INVOICE' and source_id=? and revoked_at is null
                """,Timestamp.from(clock.now()),current.invoiceId());
            // Preserve other trial/legacy/paid grants; only revoke the refunded invoice grant.
            jdbc.update("""
                update merchant_subscriptions set status='REVOKED',updated_at=?,version=version+1
                where merchant_id=? and not exists(
                  select 1 from billing_entitlement_grants g
                  where g.merchant_id=? and g.revoked_at is null
                    and g.valid_from<=? and g.valid_until>?
                )
                """,Timestamp.from(clock.now()),current.merchantId(),current.merchantId(),
                Timestamp.from(clock.now()),Timestamp.from(clock.now()));
        }
        boolean finalState=!"REVIEWING".equals(request.status());
        jdbc.update("""
            update billing_finance_cases
            set status=?,resolution_note=?,external_refund_reference=?,resolved_by_user_id=?,resolved_at=?,
                reviewed_by_user_id=case when ?='REVIEWING' then ? else reviewed_by_user_id end,
                reviewed_at=case when ?='REVIEWING' then ? else reviewed_at end
            where id=?
            """,request.status(),request.note().trim(),request.externalReference(),
            finalState?actor.userId():null,finalState?Timestamp.from(clock.now()):null,
            request.status(),actor.userId(),request.status(),Timestamp.from(clock.now()),caseId);
        audit.securityEvent(actor.userId(),"BILLING_FINANCE_CASE_"+request.status(),
            "BILLING_FINANCE_CASE",caseId,requestIds.current(),
            Map.of("invoiceId",current.invoiceId().toString(),"status",request.status()));
        return details(caseId);
    }

    /**
     * A refund must first be reviewed by a different Admin, and the requester
     * cannot independently confirm their own reimbursement.
     */
    static void requireIndependentRefundApproval(String status,UUID approver,UUID requester,UUID reviewer) {
        if(!"REVIEWING".equals(status) || reviewer==null || reviewer.equals(approver)
                || requester==null || requester.equals(approver) || requester.equals(reviewer))
            throw conflict("Refund requires review by another Administrator and an independent approver.");
    }

    @Transactional(readOnly=true)
    public Case details(UUID id){
        var rows=jdbc.query("select * from billing_finance_cases where id=?",this::map,id);
        if(rows.isEmpty())throw notFound();
        return rows.getFirst();
    }

    private Case map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        var resolved=rs.getTimestamp("resolved_at");
        return new Case((UUID)rs.getObject("id"),(UUID)rs.getObject("invoice_id"),
            (UUID)rs.getObject("merchant_id"),rs.getString("kind"),rs.getString("status"),
            rs.getString("reason"),rs.getTimestamp("created_at").toInstant(),
            resolved==null?null:resolved.toInstant(),rs.getString("resolution_note"));
    }
    private static ApiException invalid(String s){return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,s);}
    private static ApiException conflict(String s){return new ApiException(ErrorCode.BILLING_PAYMENT_UNAVAILABLE,HttpStatus.CONFLICT,s);}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Finance case not found.");}
    public record Request(String kind,String reason){}
    public record Resolution(String status,String note,String externalReference){}
    public record Case(UUID id,UUID invoiceId,UUID merchantId,String kind,String status,String reason,
                       Instant createdAt,Instant resolvedAt,String resolutionNote){}
}
