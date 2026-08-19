package vn.covie.location.application;

import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.persistence.UserAddressRepository;

@Component
public final class LocationContextResolver {
    private static final ZoneId VIETNAM_TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CurrentActorProvider actors;
    private final UserAddressRepository addresses;
    private final GeoPointFactory points;

    public LocationContextResolver(
            CurrentActorProvider actors, UserAddressRepository addresses, GeoPointFactory points) {
        this.actors = actors;
        this.addresses = addresses;
        this.points = points;
    }

    public LocationContext resolveForDiscovery(Double lat, Double lng, UUID addressId) {
        boolean hasCoordinates = lat != null || lng != null;
        if (hasCoordinates && (lat == null || lng == null)) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_INVALID,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Latitude and longitude must be provided together.");
        }
        if (hasCoordinates && addressId != null) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_AMBIGUOUS,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Use coordinates or addressId, not both.");
        }
        if (addressId != null) {
            CurrentActor actor = actors.currentActor();
            UserAddressEntity address = addresses.findByIdAndUserIdAndDeletedAtIsNull(addressId, actor.userId())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
            return new LocationContext(
                    LocationResolutionSource.SAVED_ADDRESS,
                    address.getGeoPoint(),
                    address.getLocationAreaId(),
                    address.getId(),
                    VIETNAM_TIMEZONE);
        }
        if (lat != null) {
            return new LocationContext(
                    LocationResolutionSource.COORDINATES,
                    points.point(lat, lng),
                    null,
                    null,
                    VIETNAM_TIMEZONE);
        }
        throw new ApiException(
                ErrorCode.LOCATION_CONTEXT_REQUIRED,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Discovery requires a location context.");
    }
}
