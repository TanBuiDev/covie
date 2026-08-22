package vn.covie.outbox.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.shared.outbox.OutboxEventEntity;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.outbox.OutboxEventStatus;
import vn.covie.shared.time.AppClock;

class OutboxFailureServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void schedulesExponentialRetryAndSanitizesTheFailure() {
        var events=mock(OutboxEventRepository.class); var clock=mock(AppClock.class);
        UUID id=UUID.randomUUID(); var event=pending(id); when(events.lockById(id)).thenReturn(Optional.of(event)); when(clock.now()).thenReturn(NOW);

        new OutboxFailureService(events,new OutboxRetryPolicy(3,2,20),clock).recordFailure(id,new IllegalStateException("temporary\nproblem"));

        assertEquals(OutboxEventStatus.FAILED,event.getStatus()); assertEquals(1,event.getAttempts());
        assertEquals(NOW.plusSeconds(2),event.getNextAttemptAt());
        assertEquals("IllegalStateException: temporary problem",event.getLastError());
    }

    @Test
    void stopsRetryingAtTheConfiguredMaximumAttempt() {
        var events=mock(OutboxEventRepository.class); var clock=mock(AppClock.class);
        UUID id=UUID.randomUUID(); var event=pending(id); event.markFailed(2,NOW,"previous"); when(events.lockById(id)).thenReturn(Optional.of(event));

        new OutboxFailureService(events,new OutboxRetryPolicy(3,1,20),clock).recordFailure(id,new RuntimeException("final"));

        assertEquals(OutboxEventStatus.FAILED,event.getStatus()); assertEquals(3,event.getAttempts()); assertNull(event.getNextAttemptAt());
        verifyNoInteractions(clock);
    }

    @Test
    void neverTurnsAnAlreadyPublishedEventBackIntoFailed() {
        var events=mock(OutboxEventRepository.class); var clock=mock(AppClock.class);
        UUID id=UUID.randomUUID(); var event=pending(id); event.markPublished(NOW); when(events.lockById(id)).thenReturn(Optional.of(event));

        new OutboxFailureService(events,new OutboxRetryPolicy(3,1,20),clock).recordFailure(id,new RuntimeException("late"));

        assertEquals(OutboxEventStatus.PUBLISHED,event.getStatus()); assertEquals(0,event.getAttempts()); verifyNoInteractions(clock);
    }

    private static OutboxEventEntity pending(UUID id) {
        return OutboxEventEntity.pending(id,"REVIEW_CREATED","review",UUID.randomUUID(),new ObjectMapper().createObjectNode(),NOW);
    }
}
