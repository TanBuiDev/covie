package vn.covie.orders.persistence;

import java.util.UUID; import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.orders.model.OrderEventEntity;

public interface OrderEventRepository extends JpaRepository<OrderEventEntity, UUID> { List<OrderEventEntity> findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId); }
