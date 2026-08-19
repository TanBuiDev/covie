package vn.covie.merchants.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MerchantProfileEntity;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfileEntity, UUID> {
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<MerchantProfileEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MerchantProfileEntity profile where profile.id = :id and profile.deletedAt is null")
    Optional<MerchantProfileEntity> findActiveForUpdate(@Param("id") UUID id);
}
