package vn.covie.merchants.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MerchantContactMethodEntity;

public interface MerchantContactMethodRepository extends JpaRepository<MerchantContactMethodEntity, UUID> {
    List<MerchantContactMethodEntity> findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(
            UUID merchantId, vn.covie.merchants.model.ContactStatus status);
    Optional<MerchantContactMethodEntity> findByIdAndMerchantProfileIdAndStatus(
            UUID id, UUID merchantId, vn.covie.merchants.model.ContactStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select contact from MerchantContactMethodEntity contact
            where contact.id = :id
              and contact.merchantProfileId = :merchantId
              and contact.status = vn.covie.merchants.model.ContactStatus.ACTIVE
            """)
    Optional<MerchantContactMethodEntity> findActiveForUpdate(@Param("id") UUID id,
            @Param("merchantId") UUID merchantId);
    List<MerchantContactMethodEntity> findAllByMerchantProfileIdAndMerchantLocationIdAndContactTypeAndStatus(
            UUID merchantId, UUID locationId, vn.covie.merchants.model.ContactType type,
            vn.covie.merchants.model.ContactStatus status);
}
