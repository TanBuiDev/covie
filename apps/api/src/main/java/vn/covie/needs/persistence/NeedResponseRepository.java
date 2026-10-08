package vn.covie.needs.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseStatus;

public interface NeedResponseRepository extends JpaRepository<NeedResponseEntity, UUID> {
    @Query(value = "select * from need_responses where id = :responseId and need_request_id = :needId for update", nativeQuery = true)
    Optional<NeedResponseEntity> lockByNeedAndId(@Param("needId") UUID needId, @Param("responseId") UUID responseId);

    Optional<NeedResponseEntity> findByNeedRequestIdAndMerchantLocationIdAndResponseStatus(
            UUID needRequestId, UUID merchantLocationId, NeedResponseStatus responseStatus);

    long countByNeedRequestIdAndResponseStatus(UUID needRequestId, NeedResponseStatus responseStatus);

    List<NeedResponseEntity> findByNeedRequestIdAndResponseStatusOrderByCreatedAtDescIdDesc(
            UUID needRequestId, NeedResponseStatus responseStatus, Pageable pageable);

    default List<NeedResponseEntity> findPage(UUID needId, NeedResponseStatus status,
            Instant afterCreatedAt, UUID afterId, Pageable pageable) {
        if (afterCreatedAt == null) {
            return findByNeedRequestIdAndResponseStatusOrderByCreatedAtDescIdDesc(needId, status, pageable);
        }
        if (afterId == null) throw new IllegalArgumentException("Cursor requires both timestamp and id.");
        return findPageAfter(needId, status, afterCreatedAt, afterId, pageable);
    }

    @Query("""
            select r from NeedResponseEntity r
            where r.needRequestId = :needId
              and r.responseStatus = :status
              and (r.createdAt < :afterCreatedAt
                   or (r.createdAt = :afterCreatedAt and r.id < :afterId))
            order by r.createdAt desc, r.id desc
            """)
    List<NeedResponseEntity> findPageAfter(@Param("needId") UUID needId,
            @Param("status") NeedResponseStatus status,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
