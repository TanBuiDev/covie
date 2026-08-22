package vn.covie.shared.outbox;

import java.time.Instant; import java.util.Optional; import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
 @Query(value="select * from outbox_events where status='PENDING' or (status='FAILED' and next_attempt_at is not null and next_attempt_at<=:now) order by occurred_at asc,id asc for update skip locked limit 1",nativeQuery=true) Optional<OutboxEventEntity> lockNextDue(@Param("now") Instant now);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OutboxEventEntity e where e.id=:id") Optional<OutboxEventEntity> lockById(@Param("id") UUID id);
}
