package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Duration;
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

/** Admin promotional/manual entitlement grants are non-monetary and individually audited. */
@Service
public class AdminBillingGrantService {
    private final JdbcTemplate jdbc;
    private final AdminAccessPolicy admins;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requests;
    private final OutboxEventWriter outbox;
    private final boolean approved;
    private BillingFinanceAccessPolicy finance;
    @Autowired public void setFinanceAccess(BillingFinanceAccessPolicy finance){this.finance=finance;}

    public AdminBillingGrantService(JdbcTemplate jdbc,AdminAccessPolicy admins,AppClock clock,
            AuditLogWriter audit,RequestIdAccessor requests,OutboxEventWriter outbox,
            @Value("${COVIE_BILLING_ADMIN_GRANTS_APPROVED:false}") boolean approved) {
        this.jdbc=jdbc;this.admins=admins;this.clock=clock;this.audit=audit;
        this.requests=requests;this.outbox=outbox;this.approved=approved;
    }

    @Transactional
    public Grant grant(UUID merchantId,Request request) {
        var actor=admins.requireAdmin();
        if(finance!=null)finance.requireApprover();
        if(!approved)throw conflict("Manual grants require an explicit operator approval.");
        Instant now=clock.now();
        if(request==null||request.validUntil()==null||!request.validUntil().isAfter(now)
                ||request.validUntil().isAfter(now.plus(Duration.ofDays(366)))
                ||request.reason()==null||request.reason().isBlank()||request.reason().length()>400){
            throw invalid("Valid end date within one year and a reason are required.");
        }
        Integer exists=jdbc.queryForObject("""
            select count(*) from merchant_profiles where id=? and deleted_at is null
            """,Integer.class,merchantId);
        if(exists==null||exists!=1)throw notFound();
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into billing_entitlement_grants
              (id,merchant_id,source_type,source_id,valid_from,valid_until,
                reason,created_by_user_id,created_at)
            values (?,?,'ADMIN',?,?,?,?,?,?)
            """,id,merchantId,id,Timestamp.from(now),Timestamp.from(request.validUntil()),
            request.reason().trim(),actor.userId(),Timestamp.from(now));
        audit.securityEvent(actor.userId(),"BILLING_ADMIN_GRANT_CREATED",
            "BILLING_ENTITLEMENT_GRANT",id,requests.current(),
            Map.of("merchantId",merchantId.toString(),"validUntil",request.validUntil().toString()));
        outbox.append(new OutboxEventIntent("BILLING_ADMIN_GRANT_CREATED",
            "BILLING_ENTITLEMENT_GRANT",id,Map.of("merchantId",merchantId.toString())));
        return new Grant(id,merchantId,"ADMIN",now,request.validUntil(),null,request.reason().trim());
    }

    @Transactional(readOnly=true)
    public List<Grant> list(UUID merchantId) {
        if(finance!=null)finance.requireReader();else admins.requireAdmin();
        return jdbc.query("""
            select id,merchant_id,source_type,valid_from,valid_until,revoked_at,reason
            from billing_entitlement_grants where merchant_id=?
            order by created_at desc limit 100
            """,(rs,n)->new Grant((UUID)rs.getObject("id"),(UUID)rs.getObject("merchant_id"),
                rs.getString("source_type"),rs.getTimestamp("valid_from").toInstant(),
                rs.getTimestamp("valid_until").toInstant(),
                rs.getTimestamp("revoked_at")==null?null:rs.getTimestamp("revoked_at").toInstant(),
                rs.getString("reason")),merchantId);
    }

    @Transactional
    public void revoke(UUID grantId,String reason) {
        var actor=admins.requireAdmin();
        if(finance!=null)finance.requireApprover();
        if(!approved)throw conflict("Grant revocation requires an explicit operator approval.");
        if(reason==null||reason.isBlank()||reason.length()>400)throw invalid("Reason is required.");
        int updated=jdbc.update("""
            update billing_entitlement_grants set revoked_at=?
            where id=? and source_type='ADMIN' and revoked_at is null
            """,Timestamp.from(clock.now()),grantId);
        if(updated!=1)throw conflict("Only an active ADMIN grant may be revoked.");
        audit.securityEvent(actor.userId(),"BILLING_ADMIN_GRANT_REVOKED",
            "BILLING_ENTITLEMENT_GRANT",grantId,requests.current(),
            Map.of("reason",reason.trim()));
    }

    private static ApiException invalid(String s){return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,s);}
    private static ApiException conflict(String s){return new ApiException(ErrorCode.BILLING_PAYMENT_UNAVAILABLE,HttpStatus.CONFLICT,s);}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Merchant not found.");}
    public record Request(Instant validUntil,String reason){}
    public record Grant(UUID id,UUID merchantId,String sourceType,Instant validFrom,Instant validUntil,
                        Instant revokedAt,String reason){}
}
