package vn.covie.merchants.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantLocationHourEntity;

public interface MerchantLocationHourRepository extends JpaRepository<MerchantLocationHourEntity, UUID> {
    List<MerchantLocationHourEntity> findAllByMerchantLocationIdOrderByDayOfWeekAscOperationTypeAscSortOrderAsc(
            UUID locationId);
    List<MerchantLocationHourEntity> findAllByMerchantLocationIdAndDayOfWeekAndOperationTypeOrderBySortOrderAsc(
            UUID locationId, short dayOfWeek, vn.covie.merchants.model.OperationType operationType);
    void deleteAllByMerchantLocationId(UUID locationId);
}
