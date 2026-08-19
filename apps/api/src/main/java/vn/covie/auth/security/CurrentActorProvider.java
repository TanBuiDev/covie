package vn.covie.auth.security;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class CurrentActorProvider {
    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication required.");
        Set<SystemRole> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                .orElseGet(java.util.List::of)
                .stream()
                .map(SystemRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentActor(UUID.fromString(jwt.getSubject()), roles, UUID.fromString(jwt.getClaimAsString("sessionId")));
    }
}
