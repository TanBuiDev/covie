package vn.covie.users.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.api.dto.MeResponse;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public class UserProfileService {
    private final CurrentActorProvider actors; private final UserRepository users; private final UserIdentityRepository identities; private final AppClock clock;
    public UserProfileService(CurrentActorProvider actors, UserRepository users, UserIdentityRepository identities, AppClock clock) { this.actors=actors; this.users=users; this.identities=identities; this.clock=clock; }
    @Transactional(readOnly = true)
    public MeResponse me() { CurrentActor actor = actors.currentActor(); return response(user(actor.userId()), actor); }
    @Transactional
    public MeResponse update(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Display name must not be blank");
        CurrentActor actor = actors.currentActor(); UserEntity user = user(actor.userId()); user.updateDisplayName(normalized, clock.now());
        return response(user, actor);
    }
    private UserEntity user(java.util.UUID userId) { return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found")); }
    private MeResponse response(UserEntity user, CurrentActor actor) {
        String email = identities.findAllByUserId(user.getId()).stream().filter(identity -> identity.getProvider() == IdentityProvider.EMAIL).map(identity -> identity.getEmailNormalized()).findFirst().orElse(null);
        List<String> roles = actor.globalRoles().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList();
        return new MeResponse(user.getId(), user.getDisplayName(), email, user.getAccountStatus().name(), user.getVersion(), roles);
    }
}
