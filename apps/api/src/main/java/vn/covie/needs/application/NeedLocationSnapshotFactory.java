package vn.covie.needs.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.covie.location.application.LocationContext;

@Component
public class NeedLocationSnapshotFactory {
    private final ObjectMapper mapper;
    public NeedLocationSnapshotFactory(ObjectMapper mapper) { this.mapper = mapper; }
    public JsonNode snapshot(LocationContext location) {
        return mapper.createObjectNode()
                .put("source", location.source().name())
                .put("addressId", location.addressId() == null ? null : location.addressId().toString())
                .put("areaId", location.locationAreaId() == null ? null : location.locationAreaId().toString())
                .put("latitude", location.point().getY())
                .put("longitude", location.point().getX())
                .put("timezone", location.timezone().getId());
    }
}
