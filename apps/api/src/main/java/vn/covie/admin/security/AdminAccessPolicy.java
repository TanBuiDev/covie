package vn.covie.admin.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public final class AdminAccessPolicy {
    private final CurrentActorProvider actors;

    public AdminAccessPolicy(CurrentActorProvider actors) {
        this.actors = actors;
    }

    public CurrentActor requireAdmin() {
        CurrentActor actor = actors.currentActor();
        if (!actor.globalRoles().contains(SystemRole.ADMIN)) {
            throw new ApiException(
                    ErrorCode.ADMIN_ROLE_REQUIRED,
                    HttpStatus.FORBIDDEN,
                    "Administrator role is required.");
        }
        return actor;
    }
}
