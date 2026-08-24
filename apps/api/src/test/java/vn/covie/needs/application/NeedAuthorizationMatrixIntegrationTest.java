package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;

class NeedAuthorizationMatrixIntegrationTest {
    private final NeedRequestRepository needs = mock(NeedRequestRepository.class);
    private final NeedResponseRepository responses = mock(NeedResponseRepository.class);
    private final AppClock clock = () -> Instant.parse("2026-08-24T12:00:00Z");
    private final NeedReadService reads = new NeedReadService(
            new CurrentActorProvider(), needs, responses, clock, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buyerCannotReadAnotherBuyersNeedAndReceives404() {
        UUID buyerA = UUID.randomUUID();
        UUID buyerB = UUID.randomUUID();
        UUID needId = UUID.randomUUID();
        authenticate(buyerA);
        when(needs.findByIdAndBuyerUserId(needId, buyerA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reads.detail(needId))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        org.mockito.Mockito.verify(needs).findByIdAndBuyerUserId(needId, buyerA);
        org.mockito.Mockito.verify(needs, org.mockito.Mockito.never()).findByIdAndBuyerUserId(needId, buyerB);
    }

    private static void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("roles", Set.of("USER"))
                .claim("sessionId", UUID.randomUUID().toString())
                .issuedAt(Instant.parse("2026-08-24T11:00:00Z"))
                .expiresAt(Instant.parse("2026-08-24T13:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, jwt.getTokenValue(), java.util.List.of()));
    }
}
