package vn.covie.location.application;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record LocationContext(
        LocationResolutionSource source,
        Point point,
        UUID locationAreaId,
        UUID addressId,
        ZoneId timezone) {
    public LocationContext {
        Objects.requireNonNull(source);
        Objects.requireNonNull(point);
        Objects.requireNonNull(timezone);
    }
}
