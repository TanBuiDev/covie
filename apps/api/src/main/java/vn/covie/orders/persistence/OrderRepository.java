package vn.covie.orders.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.orders.model.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    boolean existsByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    List<OrderEntity> findAllByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);
}
