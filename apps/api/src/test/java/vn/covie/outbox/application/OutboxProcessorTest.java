package vn.covie.outbox.application;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.realtime.SseSignal;
import vn.covie.realtime.SseSignalPublisher;

class OutboxProcessorTest {
    @Test
    void publishesSignalsOnlyAfterTransactionReturnsSuccessfully() {
        var transactions=mock(OutboxTransactionalProcessor.class); var failures=mock(OutboxFailureService.class); var sse=mock(SseSignalPublisher.class);
        UUID event=UUID.randomUUID(); var signals=List.of(new SseSignal(UUID.randomUUID(),"notification.created",Map.of("notificationId",UUID.randomUUID().toString())));
        when(transactions.processOneTransaction()).thenReturn(new OutboxProcessingResult(event,signals));
        new OutboxProcessor(transactions,failures,sse).processOne();
        var order=inOrder(transactions,sse); order.verify(transactions).processOneTransaction(); order.verify(sse).publish(signals); verifyNoInteractions(failures);
    }

    @Test
    void handlerFailureRecordsRetryAndDoesNotPublishSignals() {
        var transactions=mock(OutboxTransactionalProcessor.class); var failures=mock(OutboxFailureService.class); var sse=mock(SseSignalPublisher.class);
        UUID event=UUID.randomUUID(); RuntimeException cause=new RuntimeException("transient"); when(transactions.processOneTransaction()).thenThrow(new OutboxProcessingException(event,cause));
        new OutboxProcessor(transactions,failures,sse).processOne();
        verify(failures).recordFailure(event,cause); verifyNoInteractions(sse);
    }
}
