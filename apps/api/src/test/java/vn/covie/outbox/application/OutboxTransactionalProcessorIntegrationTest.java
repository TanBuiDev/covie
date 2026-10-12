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

@org.springframework.context.annotation.Import(OutboxTransactionalProcessorIntegrationTest.PartialFailureConfiguration.class)
@org.springframework.boot.test.context.SpringBootTest(properties = "covie.outbox.max-events-per-poll=0")
@org.springframework.test.context.ActiveProfiles("test")
class OutboxTransactionalProcessorIntegrationTest {
    // Queue-wide assertions require a database independent of other suites and their background jobs.
    static final org.testcontainers.containers.PostgreSQLContainer<?> POSTGRES =
            new org.testcontainers.containers.PostgreSQLContainer<>(
                    org.testcontainers.utility.DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("outbox_test").withUsername("covie").withPassword("covie_test");
    static { POSTGRES.start(); }
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired OutboxEventWriter writer;
    @Autowired OutboxEventRepository events;
    @Autowired OutboxProcessor processor;
    @Autowired TransactionTemplate transactions;
    @Autowired vn.covie.users.persistence.UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired OutboxFailureService failures;
    @Autowired OutboxRetryPolicy retry;

    @org.junit.jupiter.api.BeforeEach
    void isolateQueueFromOtherIntegrationSuites() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("outbox_test");
        jdbc.execute("truncate table outbox_events cascade");
    }

    @Test
    void exhaustedRetriesRemainFailedAndAreNotClaimedAgain() {
        UUID eventId = transactions.execute(status -> writer.append(new OutboxEventIntent("TEST_NOOP", "test", UUID.randomUUID(), Map.of())));
        for (int attempt = 0; attempt < retry.maxAttempts(); attempt++) failures.recordFailure(eventId, new IllegalStateException("Poison event"));
        var exhausted = events.findById(eventId).orElseThrow();
        assertThat(exhausted.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(exhausted.getAttempts()).isEqualTo(retry.maxAttempts());
        assertThat(exhausted.getNextAttemptAt()).isNull();
        assertThat(processor.processOne()).isFalse();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @org.junit.jupiter.api.RepeatedTest(20)
    void overlappingWorkersPublishOneEventOnlyOnce() throws Exception {
        UUID eventId = transactions.execute(status -> writer.append(new OutboxEventIntent("TEST_NOOP", "test", UUID.randomUUID(), Map.of())));
        var ready = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> worker = () -> { ready.await(5, java.util.concurrent.TimeUnit.SECONDS); return processor.processOne(); };
            var results = pool.invokeAll(java.util.List.of(worker, worker));
            int processed = 0;
            for (var result : results) if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) processed++;
            assertThat(processed).isEqualTo(1);
        }
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void partialHandlerWriteRollsBackAndRetryCreatesOneNotification() {
        UUID userId = UUID.randomUUID();
        users.saveAndFlush(vn.covie.users.model.UserEntity.active(userId, "Outbox retry", java.time.Instant.now()));
        UUID eventId = transactions.execute(status -> writer.append(new OutboxEventIntent(
                "TEST_PARTIAL_FAILURE", "test", userId, Map.of("userId", userId))));
        assertThat(processor.processOne()).isTrue();
        var failed = events.findById(eventId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(jdbc.queryForObject("select attempts from outbox_events where id=?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?", Integer.class, eventId)).isZero();
        jdbc.update("update outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), eventId);
        assertThat(processor.processOne()).isTrue();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(processor.processOne()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_deliveries d join notifications n on n.id=d.notification_id where n.source_outbox_event_id=?", Integer.class, eventId)).isEqualTo(1);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class PartialFailureConfiguration {
        @org.springframework.context.annotation.Bean
        OutboxEventHandler partialFailureHandler(vn.covie.notifications.application.NotificationService notifications) {
            var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
            return new OutboxEventHandler() {
                public boolean supports(String type) { return type.equals("TEST_PARTIAL_FAILURE"); }
                public java.util.List<vn.covie.realtime.SseSignal> handle(OutboxEventContext event) {
                    UUID userId = UUID.fromString(event.payload().get("userId").asText());
                    notifications.createFromOutbox(event.eventId(), userId,
                            vn.covie.notifications.model.NotificationType.ORDER_COMPLETED,
                            "Test", "Test", "test", userId, Map.of());
                    if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected failure after notification write");
                    return java.util.List.of();
                }
            };
        }
    }

    @Test
    void claimsAndPublishesOneCommittedEventExactlyOnce() {
        UUID eventId=transactions.execute(status -> writer.append(new OutboxEventIntent("TEST_NOOP","test",UUID.randomUUID(),Map.of("safe","value"))));

        assertThat(processor.processOne()).isTrue();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(processor.processOne()).isFalse();
    }
}
