package vn.covie.needs.persistence;

import java.util.Optional;
import java.util.UUID;
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
}
