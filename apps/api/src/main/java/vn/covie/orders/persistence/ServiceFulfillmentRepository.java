package vn.covie.orders.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.orders.model.ServiceFulfillmentEntity;

public interface ServiceFulfillmentRepository extends JpaRepository<ServiceFulfillmentEntity, UUID> {}
