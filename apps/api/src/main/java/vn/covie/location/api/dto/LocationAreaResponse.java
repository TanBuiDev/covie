package vn.covie.location.api.dto;

import java.util.UUID;
import vn.covie.location.model.LocationAreaEntity;
import vn.covie.location.model.LocationAreaStatus;
import vn.covie.location.model.LocationAreaType;

public record LocationAreaResponse(
        UUID id,
        UUID parentId,
        LocationAreaType areaType,
        String name,
        String slug,
        LocationAreaStatus status,
        int sortOrder) {
    public static LocationAreaResponse from(LocationAreaEntity area) {
        return new LocationAreaResponse(
                area.getId(),
                area.getParentId(),
                area.getAreaType(),
                area.getName(),
                area.getSlug(),
                area.getStatus(),
                area.getSortOrder());
    }
}
