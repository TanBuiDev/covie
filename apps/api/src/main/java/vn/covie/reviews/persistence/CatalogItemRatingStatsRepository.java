package vn.covie.reviews.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.reviews.model.CatalogItemRatingStatsEntity;

public interface CatalogItemRatingStatsRepository extends JpaRepository<CatalogItemRatingStatsEntity, UUID> {
    @Modifying
    @Query(value = "insert into catalog_item_rating_stats(catalog_item_id, review_count, rating_sum, average_rating, updated_at) values (:catalogItemId, 0, 0, 0, :now) on conflict (catalog_item_id) do nothing", nativeQuery = true)
    void ensure(@Param("catalogItemId") UUID catalogItemId, @Param("now") java.time.Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogItemRatingStatsEntity s where s.catalogItemId = :catalogItemId")
    Optional<CatalogItemRatingStatsEntity> findByCatalogItemIdForUpdate(@Param("catalogItemId") UUID catalogItemId);
}
