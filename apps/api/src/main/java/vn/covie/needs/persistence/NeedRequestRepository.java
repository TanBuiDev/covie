package vn.covie.needs.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.needs.model.NeedRequestEntity;

public interface NeedRequestRepository extends JpaRepository<NeedRequestEntity, UUID> {
    @Query(value = "select * from need_requests where id = :id for update", nativeQuery = true)
    Optional<NeedRequestEntity> lockById(@Param("id") UUID id);
}
