package vn.covie.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class IdempotencyExecutor {
    private final JdbcTemplate jdbc;
    private final IdempotencyRecordRepository repository;
    private final IdempotencyHasher hasher;
    private final ObjectMapper mapper;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public IdempotencyExecutor(
            JdbcTemplate jdbc,
            IdempotencyRecordRepository repository,
            IdempotencyHasher hasher,
            ObjectMapper mapper,
            UuidV7Generator ids,
            AppClock clock) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.hasher = hasher;
        // Numeric JSON timestamps can lose nanoseconds when persisted and replayed.
        this.mapper = mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public <T> T execute(
            UUID actorId,
            String operation,
            String rawKey,
            Object requestBody,
            int successStatus,
            Class<T> responseType,
            Supplier<T> command) {
        return executeWithStatus(actorId, operation, rawKey, requestBody, responseType,
                () -> new StoredResponse<>(successStatus, command.get())).response();
    }

    @Transactional
    public <T> Execution<T> executeWithStatus(
            UUID actorId,
            String operation,
            String rawKey,
            Object requestBody,
            Class<T> responseType,
            Supplier<StoredResponse<T>> command) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required.");
        }

        String keyHash = hasher.sha256(rawKey);
        String requestHash = hasher.sha256(requestBody);
        long lockKey = hasher.advisoryLockKey(actorId, operation, rawKey);
        jdbc.query(
                "select pg_advisory_xact_lock(?)",
                ps -> ps.setLong(1, lockKey),
                rs -> {});

        var existing = repository.findByActorIdAndOperationAndIdempotencyKeyHash(actorId, operation, keyHash);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.requestHash().equals(requestHash)) {
                throw new ApiException(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST,
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used with another request.");
            }
            if (record.status() == IdempotencyStatus.COMPLETED) {
                return new Execution<>(mapper.convertValue(record.responseBody(), responseType), true);
            }
        }

        var record = existing.orElseGet(() -> repository.save(IdempotencyRecordEntity.inProgress(
                ids.generate(), actorId, operation, keyHash, requestHash,
                clock.now(), clock.now().plus(Duration.ofHours(24)))));

        StoredResponse<T> result = command.get();
        record.complete(result.status(), mapper.valueToTree(result.response()));
        // Paged maintenance commands can clear the persistence context before returning.
        repository.save(record);
        return new Execution<>(result.response(), false);
    }

    public record StoredResponse<T>(int status, T response) {}
    public record Execution<T>(T response, boolean replayed) {}
}
