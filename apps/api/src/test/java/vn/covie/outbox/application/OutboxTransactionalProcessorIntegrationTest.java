package vn.covie.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.outbox.OutboxEventStatus;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.support.PostgresIntegrationTest;

class OutboxTransactionalProcessorIntegrationTest extends PostgresIntegrationTest {
    @Autowired OutboxEventWriter writer;
    @Autowired OutboxEventRepository events;
    @Autowired OutboxProcessor processor;
    @Autowired TransactionTemplate transactions;

    @Test
    void claimsAndPublishesOneCommittedEventExactlyOnce() {
        UUID eventId=transactions.execute(status -> writer.append(new OutboxEventIntent("TEST_NOOP","test",UUID.randomUUID(),Map.of("safe","value"))));

        assertThat(processor.processOne()).isTrue();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(processor.processOne()).isFalse();
    }
}
