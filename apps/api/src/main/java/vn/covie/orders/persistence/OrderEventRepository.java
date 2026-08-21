package vn.covie.orders.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.orders.model.OrderEventEntity;

public interface OrderEventRepository extends JpaRepository<OrderEventEntity, UUID> {}
