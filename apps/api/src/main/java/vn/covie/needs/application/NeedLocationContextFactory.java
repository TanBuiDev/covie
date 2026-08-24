package vn.covie.needs.application;

import java.time.ZoneId;
import org.springframework.stereotype.Component;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.needs.model.NeedRequestEntity;

@Component
public class NeedLocationContextFactory {
    public LocationContext from(NeedRequestEntity need) {
        String timezone = need.getLocationSnapshot().path("timezone").asText("UTC");
        return new LocationContext(LocationResolutionSource.COORDINATES, need.getGeoPoint(),
                need.getLocationAreaId(), null, ZoneId.of(timezone));
    }
}
