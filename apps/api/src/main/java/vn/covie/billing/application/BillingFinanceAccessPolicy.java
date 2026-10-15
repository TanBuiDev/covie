package vn.covie.billing.application;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

/**
 * Optional role-based finance access within global Admin. Defaults to preserving
 * existing Admin-only behavior until billing.finance_roles_enabled is activated.
 * Role assignment also needs explicit ops approval and a different Admin operator.
 */
@Service
public class BillingFinanceAccessPolicy {
    private final JdbcTemplate jdbc;
    private final AdminAccessPolicy admins;
    private final SystemSettingResolver settings;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final boolean assignmentsApproved;

    public BillingFinanceAccessPolicy(JdbcTemplate jdbc,AdminAccessPolicy admins,
            SystemSettingResolver settings,AppClock clock,AuditLogWriter audit,
            RequestIdAccessor requestIds,
            @Value("${COVIE_BILLING_FINANCE_ROLE_ASSIGNMENT_APPROVED:false}") boolean assignmentsApproved){
        this.jdbc=jdbc;this.admins=admins;this.settings=settings;this.clock=clock;
        this.audit=audit;this.requestIds=requestIds;this.assignmentsApproved=assignmentsApproved;
    }

    public CurrentActor requireReader(){return requireRank(1);}
    public CurrentActor requireOperator(){return requireRank(2);}
    public CurrentActor requireApprover(){return requireRank(3);}

    private CurrentActor requireRank(int required){
        var actor=admins.requireAdmin();
        if(!settings.bool("billing.finance_roles_enabled",SystemSettingContext.globalOnly()))
            return actor;
        String role=jdbc.query("""
            select finance_role from billing_finance_permissions where user_id=?
            """,rs->rs.next()?rs.getString(1):null,actor.userId());
        int rank=switch(role==null?"":role){
            case "READER"->1;
            case "OPERATOR"->2;
            case "APPROVER"->3;
            default->0;
        };
        if(rank<required)throw new ApiException(ErrorCode.ACCESS_DENIED,HttpStatus.FORBIDDEN,
            "Billing financial permissions are required.");
        return actor;
    }

    @Transactional(readOnly=true)
    public List<Assignment> list(){
        admins.requireAdmin();
        return jdbc.query("""
            select user_id,finance_role,assigned_by_user_id,updated_at
            from billing_finance_permissions order by updated_at desc limit 100
            """,(rs,i)->new Assignment((UUID)rs.getObject("user_id"),
                rs.getString("finance_role"),(UUID)rs.getObject("assigned_by_user_id"),
                rs.getTimestamp("updated_at").toInstant()));
    }

    @Transactional
    public Assignment assign(UUID target,String role){
        var actor=admins.requireAdmin();
        verifyOps(actor.userId(),target);
        if(!List.of("READER","OPERATOR","APPROVER").contains(role))
            throw invalid("Invalid finance permission.");
        Integer exists=jdbc.queryForObject("select count(*) from users where id=?",
            Integer.class,target);
        if(exists==null||exists!=1)throw invalid("Unknown finance Admin account.");
        // Global ADMIN validation of target remains part of the identity process:
        // this table alone never grants system Admin role.
        var at=Timestamp.from(clock.now());
        jdbc.update("""
            insert into billing_finance_permissions
              (user_id,finance_role,assigned_by_user_id,assigned_at,updated_at)
            values (?,?,?,?,?)
            on conflict(user_id) do update set finance_role=excluded.finance_role,
              assigned_by_user_id=excluded.assigned_by_user_id,updated_at=excluded.updated_at
            """,target,role,actor.userId(),at,at);
        audit.securityEvent(actor.userId(),"BILLING_FINANCE_ROLE_ASSIGNED",
            "BILLING_FINANCE_PERMISSION",target,requestIds.current(),
            Map.of("targetUserId",target.toString(),"financeRole",role));
        return new Assignment(target,role,actor.userId(),clock.now());
    }

    @Transactional
    public void revoke(UUID target){
        var actor=admins.requireAdmin();
        verifyOps(actor.userId(),target);
        int n=jdbc.update("delete from billing_finance_permissions where user_id=?",target);
        if(n!=1)throw invalid("Finance permission does not exist.");
        audit.securityEvent(actor.userId(),"BILLING_FINANCE_ROLE_REVOKED",
            "BILLING_FINANCE_PERMISSION",target,requestIds.current(),
            Map.of("targetUserId",target.toString()));
    }

    private void verifyOps(UUID actor,UUID target){
        if(!assignmentsApproved)throw new ApiException(ErrorCode.ACCESS_DENIED,HttpStatus.FORBIDDEN,
            "Finance role assignment requires separate operator approval.");
        if(target==null||target.equals(actor))throw invalid(
            "A second Administrator must assign or revoke a user's finance permissions.");
    }

    private static ApiException invalid(String text){
        return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,text);
    }

    public record Assignment(UUID userId,String role,UUID assignedBy,java.time.Instant updatedAt){}
}
