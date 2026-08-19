package vn.covie.shared.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    Optional<IdempotencyRecordEntity> findByActorIdAndOperationAndIdempotencyKeyHash(
            UUID actorId, String operation, String idempotencyKeyHash);
}
