package vn.covie.location.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.location.model.LocationAreaEntity;
import vn.covie.location.model.LocationAreaStatus;

public interface LocationAreaRepository extends JpaRepository<LocationAreaEntity, UUID> {
    List<LocationAreaEntity> findAllByParentIdIsNullAndStatusOrderBySortOrderAscNameAsc(LocationAreaStatus status);

    List<LocationAreaEntity> findAllByParentIdAndStatusOrderBySortOrderAscNameAsc(
            UUID parentId, LocationAreaStatus status);
}
