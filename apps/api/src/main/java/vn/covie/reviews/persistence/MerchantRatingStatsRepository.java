package vn.covie.reviews.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.reviews.model.MerchantRatingStatsEntity;

public interface MerchantRatingStatsRepository extends JpaRepository<MerchantRatingStatsEntity, UUID> {
    @Modifying
    @Query(value = "insert into merchant_rating_stats(merchant_profile_id, review_count, rating_sum, average_rating, updated_at) values (:merchantProfileId, 0, 0, 0, :now) on conflict (merchant_profile_id) do nothing", nativeQuery = true)
    void ensure(@Param("merchantProfileId") UUID merchantProfileId, @Param("now") java.time.Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MerchantRatingStatsEntity s where s.merchantProfileId = :merchantProfileId")
    Optional<MerchantRatingStatsEntity> findByMerchantProfileIdForUpdate(@Param("merchantProfileId") UUID merchantProfileId);
}
