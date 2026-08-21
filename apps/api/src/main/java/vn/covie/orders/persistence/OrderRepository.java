package vn.covie.orders.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.orders.model.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    boolean existsByOrderCode(String orderCode);
}
