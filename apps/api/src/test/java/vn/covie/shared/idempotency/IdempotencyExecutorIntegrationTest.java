package vn.covie.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class IdempotencyExecutorIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    IdempotencyExecutor executor;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void persistedResponseRetainsAllInstantNanosecondsOnReplay() {
        UUID actorId = activeUser();
        Instant instant = Instant.parse("2026-10-11T07:00:00.123456789Z");
        AtomicInteger executions = new AtomicInteger();
        TimedResponse first = executor.execute(actorId, "precision.test", "nanosecond-response", Map.of(), 201,
                TimedResponse.class, () -> new TimedResponse(instant, executions.incrementAndGet()));
        TimedResponse replay = executor.execute(actorId, "precision.test", "nanosecond-response", Map.of(), 201,
                TimedResponse.class, () -> new TimedResponse(Instant.EPOCH, executions.incrementAndGet()));
        assertThat(replay).isEqualTo(first);
        assertThat(replay.createdAt()).isEqualTo(instant);
        assertThat(executions).hasValue(1);
        assertThat(jdbc.queryForObject("select response_body->>'createdAt' from idempotency_records where actor_id=?",
                String.class, actorId)).isEqualTo(instant.toString());
    }

    @Test
    void commandClearingPersistenceContextStillStoresCompletedResponseForReplay() {
        UUID actorId=activeUser();
        AtomicInteger executions=new AtomicInteger();
        StoredResponse first=executor.execute(actorId,"maintenance.test","clearing-command",Map.of(),200,
                StoredResponse.class,()->{
                    entityManager.flush();
                    entityManager.clear();
                    return new StoredResponse("rebuilt",executions.incrementAndGet());
                });
        StoredResponse replay=executor.execute(actorId,"maintenance.test","clearing-command",Map.of(),200,
                StoredResponse.class,()->new StoredResponse("duplicate",executions.incrementAndGet()));
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
        assertThat(jdbc.queryForObject("select status from idempotency_records where actor_id=?",String.class,actorId)).isEqualTo("COMPLETED");
    }

    @Test
    void sameActorOperationKeyAndBodyReturnsStoredResultWithoutRunningCommandTwice() {
        UUID actorId = activeUser();
        AtomicInteger executions = new AtomicInteger();
        Map<String, Object> request = Map.of("name", "Iced coffee");

        StoredResponse first = executor.execute(actorId, "catalog.create", "create-catalog-item", request,
                201, StoredResponse.class, () -> new StoredResponse("catalog-item-1", executions.incrementAndGet()));
        StoredResponse replay = executor.execute(actorId, "catalog.create", "create-catalog-item", request,
                201, StoredResponse.class, () -> new StoredResponse("catalog-item-2", executions.incrementAndGet()));

        assertThat(first).isEqualTo(new StoredResponse("catalog-item-1", 1));
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
        assertThat(jdbc.queryForObject("select response_status from idempotency_records where actor_id = ?", Integer.class, actorId))
                .isEqualTo(201);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsConflict() {
        UUID actorId = activeUser();
        executor.execute(actorId, "catalog.create", "same-key", Map.of("name", "Tea"), 201,
                StoredResponse.class, () -> new StoredResponse("catalog-item-1", 1));

        assertThatThrownBy(() -> executor.execute(actorId, "catalog.create", "same-key", Map.of("name", "Coffee"),
                201, StoredResponse.class, () -> new StoredResponse("catalog-item-2", 2)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST));
    }

    @Test
    void sameKeyForDifferentActorsIsIndependent() {
        UUID firstActor = activeUser();
        UUID secondActor = activeUser();

        StoredResponse first = executor.execute(firstActor, "media.complete", "same-key", Map.of("mediaId", "1"), 200,
                StoredResponse.class, () -> new StoredResponse("first", 1));
        StoredResponse second = executor.execute(secondActor, "media.complete", "same-key", Map.of("mediaId", "1"), 200,
                StoredResponse.class, () -> new StoredResponse("second", 1));

        assertThat(first).isEqualTo(new StoredResponse("first", 1));
        assertThat(second).isEqualTo(new StoredResponse("second", 1));
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where operation = 'media.complete' and actor_id in (?, ?)", Integer.class, firstActor, secondActor))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select min(response_status) from idempotency_records where operation = 'media.complete' and actor_id in (?, ?)", Integer.class, firstActor, secondActor))
                .isEqualTo(200);
    }

    @Test
    void rollbackOfBusinessCommandDoesNotLeaveCompletedIdempotencyRecord() {
        UUID actorId = activeUser();

        assertThatThrownBy(() -> executor.execute(actorId, "catalog.create", "failing-key", Map.of("name", "Tea"), 201,
                StoredResponse.class, () -> {
                    throw new IllegalStateException("Business command failed");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Business command failed");

        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id = ?", Integer.class, actorId))
                .isZero();
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> executor.execute(UUID.randomUUID(), "catalog.create", " ", Map.of(), 201,
                StoredResponse.class, () -> new StoredResponse("ignored", 0)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
    }

    private UUID activeUser() {
        UUID id = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(id, "Idempotency test user", Instant.parse("2026-08-12T00:00:00Z")));
        return id;
    }

    private record StoredResponse(String id, int executions) {}
    private record TimedResponse(Instant createdAt, int executions) {}
}
