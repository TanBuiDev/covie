package vn.covie.needs.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.needs.model.NeedRequestEntity;

public interface NeedRequestRepository extends JpaRepository<NeedRequestEntity, UUID> {
    @Query(value = "select * from need_requests where id = :id for update", nativeQuery = true)
    Optional<NeedRequestEntity> lockById(@Param("id") UUID id);

    Optional<NeedRequestEntity> findByIdAndBuyerUserId(UUID id, UUID buyerUserId);

    @Query(value = "select * from need_requests where id = :id and buyer_user_id = :buyerUserId for update", nativeQuery = true)
    Optional<NeedRequestEntity> lockByIdAndBuyerUserId(
            @Param("id") UUID id,
            @Param("buyerUserId") UUID buyerUserId);

    List<NeedRequestEntity> findByBuyerUserIdOrderByCreatedAtDescIdDesc(UUID buyerUserId);

    @Query(value = """
            select *
            from need_requests
            where request_status = 'ACTIVE'
              and expires_at <= :now
            order by expires_at asc, id asc
            for update skip locked
            limit :limit
            """, nativeQuery = true)
    List<NeedRequestEntity> claimExpiredBatch(
            @Param("now") Instant now,
            @Param("limit") int limit);
}
