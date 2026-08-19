package vn.covie.location.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.location.api.dto.LocationAreaResponse;
import vn.covie.location.model.LocationAreaStatus;
import vn.covie.location.persistence.LocationAreaRepository;

@Service
public class LocationAreaService {
    private final LocationAreaRepository areas;

    public LocationAreaService(LocationAreaRepository areas) {
        this.areas = areas;
    }

    @Transactional(readOnly = true)
    public List<LocationAreaResponse> activeAreas(UUID parentId) {
        if (parentId == null) {
            return areas.findAllByParentIdIsNullAndStatusOrderBySortOrderAscNameAsc(LocationAreaStatus.ACTIVE).stream()
                    .map(LocationAreaResponse::from)
                    .toList();
        }
        return areas.findAllByParentIdAndStatusOrderBySortOrderAscNameAsc(parentId, LocationAreaStatus.ACTIVE).stream()
                .map(LocationAreaResponse::from)
                .toList();
    }
}
