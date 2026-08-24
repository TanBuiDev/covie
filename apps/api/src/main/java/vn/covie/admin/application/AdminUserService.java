package vn.covie.admin.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.api.dto.AdminUserCommandResult;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public class AdminUserService {
    private final AdminAccessPolicy access; private final UserRepository users; private final SystemRoleAssignmentRepository roles;
    private final AuthSessionRepository sessions; private final AuditLogWriter audit; private final OutboxEventWriter outbox;
    private final AppClock clock; private final RequestIdAccessor requestIds;
    public AdminUserService(AdminAccessPolicy access, UserRepository users, SystemRoleAssignmentRepository roles, AuthSessionRepository sessions,
            AuditLogWriter audit, OutboxEventWriter outbox, AppClock clock, RequestIdAccessor requestIds) { this.access=access;this.users=users;this.roles=roles;this.sessions=sessions;this.audit=audit;this.outbox=outbox;this.clock=clock;this.requestIds=requestIds; }
    @Transactional public AdminUserCommandResult suspend(UUID userId, AdminReasonRequest request) {
        var actor=access.requireAdmin(); if(actor.userId().equals(userId)) throw new ApiException(ErrorCode.ADMIN_CANNOT_SUSPEND_SELF,HttpStatus.UNPROCESSABLE_ENTITY,"An administrator cannot suspend themselves.");
        UserEntity user=users.findByIdForUpdate(userId).orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"User was not found."));
        boolean targetAdmin=roles.findAllByUserId(userId).stream().anyMatch(a->a.getRole()==SystemRole.ADMIN);
        if(targetAdmin&&roles.countActiveUsersWithRole(SystemRole.ADMIN)<=1) throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_BE_SUSPENDED,HttpStatus.CONFLICT,"The last active administrator cannot be suspended.");
        try { user.suspend(clock.now()); } catch(IllegalStateException ex) { throw new ApiException(ErrorCode.ADMIN_USER_STATE_CONFLICT,HttpStatus.CONFLICT,ex.getMessage()); }
        sessions.revokeAllActive(userId,clock.now(),AuthSessionRevocationReason.ADMIN_REVOKED);
        audit.securityEvent(actor.userId(),"ADMIN_USER_SUSPENDED","USER",userId,requestIds.current(),Map.of("reasonCode",request.reasonCode()));
        outbox.append(new OutboxEventIntent("ADMIN_USER_SUSPENDED","USER",userId,Map.of("reasonCode",request.reasonCode())));
        return new AdminUserCommandResult(userId,user.getAccountStatus());
    }
    @Transactional public AdminUserCommandResult restore(UUID userId) { var actor=access.requireAdmin(); UserEntity user=users.findByIdForUpdate(userId).orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"User was not found.")); try { user.restore(clock.now()); } catch(IllegalStateException ex) { throw new ApiException(ErrorCode.ADMIN_USER_STATE_CONFLICT,HttpStatus.CONFLICT,ex.getMessage()); } audit.securityEvent(actor.userId(),"ADMIN_USER_RESTORED","USER",userId,requestIds.current(),Map.of()); outbox.append(new OutboxEventIntent("ADMIN_USER_RESTORED","USER",userId,Map.of())); return new AdminUserCommandResult(userId,user.getAccountStatus()); }
}
