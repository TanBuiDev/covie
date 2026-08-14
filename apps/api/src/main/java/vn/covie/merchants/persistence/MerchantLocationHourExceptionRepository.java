package vn.covie.merchants.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantLocationHourExceptionEntity;

public interface MerchantLocationHourExceptionRepository extends JpaRepository<MerchantLocationHourExceptionEntity, UUID> {
    List<MerchantLocationHourExceptionEntity> findAllByMerchantLocationIdAndLocalDateAndOperationTypeOrderBySortOrderAsc(
            UUID locationId, LocalDate localDate, vn.covie.merchants.model.OperationType operationType);
}
