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
    }
}
