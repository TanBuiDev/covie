package vn.covie.merchants.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.covie.merchants.model.MerchantProfileEntity;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfileEntity, UUID> {
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<MerchantProfileEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MerchantProfileEntity profile where profile.id = :id and profile.deletedAt is null")
    Optional<MerchantProfileEntity> findActiveForUpdate(@Param("id") UUID id);
    @Query("select p from MerchantProfileEntity p where p.deletedAt is null and (:status is null or p.merchantStatus = :status) and (:moderationStatus is null or p.moderationStatus = :moderationStatus) and (cast(:q as string) is null or lower(p.name) like concat('%', lower(cast(:q as string)), '%') or lower(p.slug) like concat('%', lower(cast(:q as string)), '%'))")
    Page<MerchantProfileEntity> searchAdmin(@Param("status") vn.covie.merchants.model.MerchantStatus status, @Param("moderationStatus") vn.covie.merchants.model.MerchantModerationStatus moderationStatus, @Param("q") String q, Pageable pageable);
}
