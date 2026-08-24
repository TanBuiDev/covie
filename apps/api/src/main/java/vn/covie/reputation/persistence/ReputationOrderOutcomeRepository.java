package vn.covie.reputation.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.reputation.model.ReputationOrderOutcomeEntity;

public interface ReputationOrderOutcomeRepository extends JpaRepository<ReputationOrderOutcomeEntity, UUID> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into reputation_order_outcomes(
                order_id, buyer_user_id, merchant_profile_id, buyer_outcome, merchant_outcome,
                source_order_status, source_outbox_event_id, updated_at)
            values (:orderId, :buyerUserId, :merchantProfileId, 'NONE', 'NONE',
                    :sourceOrderStatus, :sourceOutboxEventId, :now)
            on conflict (order_id) do nothing
            """, nativeQuery = true)
    int ensureNeutral(@Param("orderId") UUID orderId, @Param("buyerUserId") UUID buyerUserId,
            @Param("merchantProfileId") UUID merchantProfileId, @Param("sourceOrderStatus") String sourceOrderStatus,
            @Param("sourceOutboxEventId") UUID sourceOutboxEventId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from ReputationOrderOutcomeEntity o where o.orderId=:orderId")
    Optional<ReputationOrderOutcomeEntity> findForUpdate(@Param("orderId") UUID orderId);
}
