package vn.covie.inventory.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.inventory.model.InventoryReservationEntity;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {
    @Query("select r from InventoryReservationEntity r where r.orderId = :orderId "
            + "and r.reservationStatus = vn.covie.inventory.model.InventoryReservationStatus.ACTIVE "
            + "order by r.locationVariantId asc")
    List<InventoryReservationEntity> findActiveByOrderIdOrdered(@Param("orderId") UUID orderId);
}
