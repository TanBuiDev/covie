package vn.covie.delivery.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.covie.delivery.model.CoverageStatus;
import vn.covie.delivery.model.CoverageType;
import vn.covie.delivery.model.DeliveryCoverageEntity;

public interface DeliveryCoverageRepository extends JpaRepository<DeliveryCoverageEntity, UUID> {
    List<DeliveryCoverageEntity> findByMerchantLocationIdOrderByCoverageType(UUID locationId);
    Optional<DeliveryCoverageEntity> findByIdAndMerchantLocationId(UUID id, UUID locationId);
    Optional<DeliveryCoverageEntity> findFirstByMerchantLocationIdAndCoverageTypeAndCoverageStatus(UUID locationId, CoverageType type, CoverageStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeliveryCoverageEntity c where c.id = :id and c.merchantLocationId = :locationId")
    Optional<DeliveryCoverageEntity> findForUpdate(@Param("id") UUID id, @Param("locationId") UUID locationId);
}
