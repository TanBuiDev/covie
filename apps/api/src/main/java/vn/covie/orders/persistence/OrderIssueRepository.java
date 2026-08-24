package vn.covie.orders.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.orders.model.OrderIssueEntity;

public interface OrderIssueRepository extends JpaRepository<OrderIssueEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from OrderIssueEntity i where i.orderId = :orderId
            and i.issueStatus = vn.covie.orders.model.OrderIssueStatus.OPEN
            """)
    Optional<OrderIssueEntity> lockOpenByOrderId(@Param("orderId") UUID orderId);

    List<OrderIssueEntity> findTop100ByIssueStatusAndCloseAfterAtLessThanEqualOrderByCloseAfterAtAsc(
            vn.covie.orders.model.OrderIssueStatus issueStatus, java.time.Instant now);
    Optional<OrderIssueEntity> findFirstByOrderIdOrderByOpenedAtDesc(UUID orderId);
}
