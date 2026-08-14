package vn.covie.merchants.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantContactMethodEntity;

public interface MerchantContactMethodRepository extends JpaRepository<MerchantContactMethodEntity, UUID> {
    List<MerchantContactMethodEntity> findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(
            UUID merchantId, vn.covie.merchants.model.ContactStatus status);
    Optional<MerchantContactMethodEntity> findByIdAndMerchantProfileIdAndStatus(
            UUID id, UUID merchantId, vn.covie.merchants.model.ContactStatus status);
    List<MerchantContactMethodEntity> findAllByMerchantProfileIdAndMerchantLocationIdAndContactTypeAndStatus(
            UUID merchantId, UUID locationId, vn.covie.merchants.model.ContactType type,
            vn.covie.merchants.model.ContactStatus status);
}
