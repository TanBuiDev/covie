package vn.covie.admin.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.api.dto.AdminUserCommandResult;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.AccountStatus;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public final class AdminUserService {
    private final AdminAccessPolicy admins; private final UserRepository users; private final SystemRoleAssignmentRepository roles;
    private final AuthSessionRepository sessions; private final IdempotencyExecutor idempotency; private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds; private final OutboxEventWriter outbox; private final AppClock clock;

    public AdminUserService(AdminAccessPolicy admins,UserRepository users,SystemRoleAssignmentRepository roles,AuthSessionRepository sessions,
            IdempotencyExecutor idempotency,AuditLogWriter audit,RequestIdAccessor requestIds,OutboxEventWriter outbox,AppClock clock){
        this.admins=admins;this.users=users;this.roles=roles;this.sessions=sessions;this.idempotency=idempotency;this.audit=audit;this.requestIds=requestIds;this.outbox=outbox;this.clock=clock;
    }

    public AdminUserCommandResult suspend(UUID userId, AdminReasonRequest request, String key) {
        CurrentActor actor=admins.requireAdmin();
        if (actor.userId().equals(userId)) throw new ApiException(ErrorCode.ADMIN_CANNOT_SUSPEND_SELF,HttpStatus.UNPROCESSABLE_ENTITY,"Administrator cannot suspend self.");
        return idempotency.execute(actor.userId(),"admin.user.suspend:"+userId,key,request,200,AdminUserCommandResult.class,
                () -> suspendLocked(actor,userId,request.reason()));
    }

    public AdminUserCommandResult restore(UUID userId, String key) {
        CurrentActor actor=admins.requireAdmin();
        return idempotency.execute(actor.userId(),"admin.user.restore:"+userId,key,Map.of(),200,AdminUserCommandResult.class,
                () -> restoreLocked(actor,userId));
    }

    private AdminUserCommandResult suspendLocked(CurrentActor actor, UUID userId, String reason) {
        UserEntity user=users.lockById(userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"User was not found."));
        if (user.getAccountStatus()!=AccountStatus.ACTIVE) throw state("User is not ACTIVE.");
        if (roles.existsByUserIdAndRole(userId,SystemRole.ADMIN) && roles.countActiveAdmins()<=1) {
            throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_BE_SUSPENDED,HttpStatus.CONFLICT,"Last active administrator cannot be suspended.");
        }
        Instant now=clock.now(); user.suspend(now); sessions.revokeAllActive(userId,now,AuthSessionRevocationReason.ADMIN_REVOKED);
        write(actor,userId,"ADMIN_USER_SUSPENDED",reason); return new AdminUserCommandResult(userId,user.getAccountStatus());
    }

    private AdminUserCommandResult restoreLocked(CurrentActor actor, UUID userId) {
        UserEntity user=users.lockById(userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"User was not found."));
        if (user.getAccountStatus()!=AccountStatus.SUSPENDED) throw state("User is not SUSPENDED.");
        user.restore(clock.now()); write(actor,userId,"ADMIN_USER_RESTORED",null); return new AdminUserCommandResult(userId,user.getAccountStatus());
    }

    private void write(CurrentActor actor, UUID userId, String event, String reason){
        Map<String,Object> payload=reason==null?Map.of("userId",userId.toString()):Map.of("userId",userId.toString(),"reason",reason);
        audit.securityEvent(actor.userId(),event,"USER",userId,requestIds.current(),payload); outbox.append(new OutboxEventIntent(event,"USER",userId,payload));
    }
    private static ApiException state(String message){return new ApiException(ErrorCode.ADMIN_USER_STATE_CONFLICT,HttpStatus.CONFLICT,message);}
}
