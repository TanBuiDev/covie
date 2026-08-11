package vn.covie.merchants.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantLocationEntity;

public interface MerchantLocationRepository extends JpaRepository<MerchantLocationEntity, UUID> {
    Optional<MerchantLocationEntity> findByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id, UUID merchantProfileId);
}
