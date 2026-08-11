package vn.covie.location.application;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class GeoPointFactory {
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public Point point(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_INVALID,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Latitude or longitude is out of range.");
        }
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
