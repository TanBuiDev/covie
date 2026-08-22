package vn.covie.reviews.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.reviews.model.MerchantRatingStatsEntity;

public interface MerchantRatingStatsRepository extends JpaRepository<MerchantRatingStatsEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MerchantRatingStatsEntity s where s.merchantProfileId = :merchantProfileId")
    Optional<MerchantRatingStatsEntity> findByMerchantProfileIdForUpdate(@Param("merchantProfileId") UUID merchantProfileId);
}
