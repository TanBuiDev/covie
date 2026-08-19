package vn.covie.delivery.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.delivery.model.DeliveryCoverageAreaEntity;
import vn.covie.delivery.model.DeliveryCoverageAreaId;

public interface DeliveryCoverageAreaRepository extends JpaRepository<DeliveryCoverageAreaEntity, DeliveryCoverageAreaId> {
    List<DeliveryCoverageAreaEntity> findByIdDeliveryCoverageId(UUID coverageId);
}
