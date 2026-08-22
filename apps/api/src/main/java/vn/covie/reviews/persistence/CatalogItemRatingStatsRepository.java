package vn.covie.reviews.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.reviews.model.CatalogItemRatingStatsEntity;

public interface CatalogItemRatingStatsRepository extends JpaRepository<CatalogItemRatingStatsEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogItemRatingStatsEntity s where s.catalogItemId = :catalogItemId")
    Optional<CatalogItemRatingStatsEntity> findByCatalogItemIdForUpdate(@Param("catalogItemId") UUID catalogItemId);
}
