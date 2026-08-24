package vn.covie.orders.persistence;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.orders.model.OrderItemEntity;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> { List<OrderItemEntity> findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId); }
