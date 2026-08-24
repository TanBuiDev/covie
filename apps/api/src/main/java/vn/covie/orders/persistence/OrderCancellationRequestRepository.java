package vn.covie.orders.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.orders.model.OrderCancellationRequestEntity;

public interface OrderCancellationRequestRepository extends JpaRepository<OrderCancellationRequestEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from OrderCancellationRequestEntity r where r.orderId = :orderId
            and r.requestStatus = vn.covie.orders.model.CancellationRequestStatus.PENDING
            """)
    Optional<OrderCancellationRequestEntity> lockPendingByOrderId(@Param("orderId") UUID orderId);
    Optional<OrderCancellationRequestEntity> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
