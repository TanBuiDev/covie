package vn.covie.reviews.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.reviews.model.ReviewEntity;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {
    Optional<ReviewEntity> findByOrderItemId(UUID orderItemId);
    boolean existsByOrderItemId(UUID orderItemId);

    @Query("select count(r), coalesce(sum(r.rating), 0) from ReviewEntity r where r.catalogItemId = :catalogItemId and r.reviewStatus = 'ACTIVE' and r.deletedAt is null")
    List<Object[]> aggregateActiveCatalogReviews(@Param("catalogItemId") UUID catalogItemId);

    @Query("select count(r), coalesce(sum(r.rating), 0) from ReviewEntity r where r.merchantProfileId = :merchantProfileId and r.reviewStatus = 'ACTIVE' and r.deletedAt is null")
    List<Object[]> aggregateActiveMerchantReviews(@Param("merchantProfileId") UUID merchantProfileId);

    @Query("""
            select r from ReviewEntity r where r.catalogItemId = :catalogItemId
            and r.reviewStatus = 'ACTIVE' and r.deletedAt is null
            and (r.createdAt < :afterCreatedAt or (r.createdAt = :afterCreatedAt and r.id < :afterId))
            order by r.createdAt desc, r.id desc
            """)
    List<ReviewEntity> findActiveCatalogReviews(@Param("catalogItemId") UUID catalogItemId,
            @Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") UUID afterId, Pageable pageable);

    @Query("select r from ReviewEntity r where r.catalogItemId = :catalogItemId and r.reviewStatus = 'ACTIVE' and r.deletedAt is null order by r.createdAt desc, r.id desc")
    List<ReviewEntity> findInitialActiveCatalogReviews(@Param("catalogItemId") UUID catalogItemId, Pageable pageable);

    default List<ReviewEntity> findActiveCatalogReviews(UUID catalogItemId, Instant afterCreatedAt, UUID afterId, int limit) {
        Pageable page = PageRequest.of(0, limit);
        return afterCreatedAt == null ? findInitialActiveCatalogReviews(catalogItemId, page)
                : findActiveCatalogReviews(catalogItemId, afterCreatedAt, afterId, page);
    }

    @Query("""
            select r from ReviewEntity r where r.merchantProfileId = :merchantProfileId
            and r.reviewStatus = 'ACTIVE' and r.deletedAt is null
            and (r.createdAt < :afterCreatedAt or (r.createdAt = :afterCreatedAt and r.id < :afterId))
            order by r.createdAt desc, r.id desc
            """)
    List<ReviewEntity> findActiveMerchantReviews(@Param("merchantProfileId") UUID merchantProfileId,
            @Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") UUID afterId, Pageable pageable);

    @Query("select r from ReviewEntity r where r.merchantProfileId = :merchantProfileId and r.reviewStatus = 'ACTIVE' and r.deletedAt is null order by r.createdAt desc, r.id desc")
    List<ReviewEntity> findInitialActiveMerchantReviews(@Param("merchantProfileId") UUID merchantProfileId, Pageable pageable);

    default List<ReviewEntity> findActiveMerchantReviews(UUID merchantProfileId, Instant afterCreatedAt, UUID afterId, int limit) {
        Pageable page = PageRequest.of(0, limit);
        return afterCreatedAt == null ? findInitialActiveMerchantReviews(merchantProfileId, page)
                : findActiveMerchantReviews(merchantProfileId, afterCreatedAt, afterId, page);
    }
}
