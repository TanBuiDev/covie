package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.checkout.config.CheckoutProperties;
import vn.covie.checkout.model.CheckoutSessionEntity;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.persistence.CheckoutSessionRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;

class CheckoutSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void returnsTheIdentifierOfThePersistedCheckoutSession() {
        CheckoutSessionRepository sessions = mock(CheckoutSessionRepository.class);
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CheckoutTokenGenerator tokens = mock(CheckoutTokenGenerator.class);
        when(tokens.generate()).thenReturn("chk_token");

        CheckoutSessionService service = new CheckoutSessionService(
                sessions, tokens, new CheckoutTokenHasher(), new CheckoutProperties(Duration.ofMinutes(15)),
                new UuidV7Generator(), () -> NOW);
        var json = new ObjectMapper().createObjectNode();

        CreatedCheckoutSession created = service.create(
                UUID.randomUUID(), CheckoutSourceType.DIRECT, json, null, json, json);

        ArgumentCaptor<CheckoutSessionEntity> persisted = ArgumentCaptor.forClass(CheckoutSessionEntity.class);
        org.mockito.Mockito.verify(sessions).save(persisted.capture());
        assertThat(created.id()).isEqualTo(persisted.getValue().getId());
        assertThat(created.checkoutToken()).isEqualTo("chk_token");
        assertThat(persisted.getValue().getTokenHash()).isEqualTo(new CheckoutTokenHasher().hash(created.checkoutToken()))
                .hasSize(64).isNotEqualTo(created.checkoutToken());
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(persisted.getValue().getExpiresAt()).isEqualTo(created.expiresAt());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {-1, 0, 1})
    void expiryDeadlineIsExclusiveAndDoesNotConsumeTheSession(long offsetNanos) {
        UUID user = UUID.randomUUID();
        String token = "checkout-boundary-token";
        CheckoutTokenHasher hasher = new CheckoutTokenHasher();
        var json = new ObjectMapper().createObjectNode();
        Instant expiry = NOW.plus(Duration.ofMinutes(15));
        CheckoutSessionEntity session = CheckoutSessionEntity.create(UUID.randomUUID(), hasher.hash(token), user,
                CheckoutSourceType.DIRECT, json, null, json, json, expiry, NOW);
        CheckoutSessionRepository sessions = mock(CheckoutSessionRepository.class);
        when(sessions.findByTokenHashForUpdate(hasher.hash(token))).thenReturn(java.util.Optional.of(session));
        CheckoutSessionService service = new CheckoutSessionService(sessions, mock(CheckoutTokenGenerator.class),
                hasher, new CheckoutProperties(Duration.ofMinutes(15)), new UuidV7Generator(),
                () -> expiry.plusNanos(offsetNanos));
        if (offsetNanos < 0) {
            assertThat(service.lock(token, user)).isSameAs(session);
        } else {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.lock(token, user))
                    .isInstanceOfSatisfying(vn.covie.shared.web.ApiException.class, failure -> {
                        assertThat(failure.code()).isEqualTo(vn.covie.shared.web.ErrorCode.CHECKOUT_SESSION_EXPIRED);
                        assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    });
        }
        assertThat(session.getConsumedAt()).isNull();
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.never()).save(any());
    }
}
