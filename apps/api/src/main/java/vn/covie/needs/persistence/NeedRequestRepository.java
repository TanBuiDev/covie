package vn.covie.needs.persistence;

import java.util.Optional;
import java.util.List;
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
    Optional<NeedRequestEntity> lockByIdAndBuyerUserId(@Param("id") UUID id, @Param("buyerUserId") UUID buyerUserId);

    List<NeedRequestEntity> findByBuyerUserIdOrderByCreatedAtDescIdDesc(UUID buyerUserId);
}
