package vn.covie.merchants.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.location.application.GeoPointFactory;
import vn.covie.location.model.LocationAreaStatus;
import vn.covie.location.persistence.LocationAreaRepository;
import vn.covie.merchants.api.dto.MerchantLocationResponse;
import vn.covie.merchants.api.dto.MerchantLocationUpsertRequest;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class MerchantLocationService {
    private final MerchantAccessPolicy access;
    private final MerchantLocationRepository locations;
    private final LocationAreaRepository areas;
    private final GeoPointFactory points;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public MerchantLocationService(MerchantAccessPolicy access, MerchantLocationRepository locations,
            LocationAreaRepository areas, GeoPointFactory points, UuidV7Generator ids, AppClock clock) {
        this.access = access; this.locations = locations; this.areas = areas; this.points = points;
        this.ids = ids; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MerchantLocationResponse> list(UUID merchantId) {
        MerchantAccess granted = access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        List<MerchantLocationEntity> visible = granted.role() == MerchantRole.STAFF
                ? locations.findAssignedActive(merchantId, granted.membershipId())
                : locations.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchantId);
        return visible.stream().map(MerchantLocationService::response).toList();
    }

    @Transactional(readOnly = true)
    public MerchantLocationResponse get(UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.VIEW_MERCHANT);
        return response(requireLocation(merchantId, locationId));
    }

    @Transactional
    public MerchantLocationResponse create(UUID merchantId, MerchantLocationUpsertRequest request) {
        access.require(merchantId, MerchantCapability.MANAGE_LOCATIONS);
        validate(request);
        MerchantLocationEntity location = MerchantLocationEntity.create(ids.generate(), merchantId,
                request.name().strip(), request.addressText().strip(), request.locationAreaId(),
                points.point(request.latitude(), request.longitude()), request.pinSource(), request.timezone().strip(),
                request.pickupEnabled(), request.sellerDeliveryEnabled(), request.serviceEnabled(),
                request.minimumDeliveryOrder(), request.currency(), clock.now());
        return response(locations.saveAndFlush(location));
    }

    @Transactional
    public MerchantLocationResponse update(UUID merchantId, UUID locationId, String ifMatch,
            MerchantLocationUpsertRequest request) {
        access.requireLocation(merchantId, locationId, MerchantCapability.MANAGE_LOCATIONS);
        MerchantLocationEntity location = requireLocation(merchantId, locationId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), location.getVersion());
        validate(request);
        location.update(request.name().strip(), request.addressText().strip(), request.locationAreaId(),
                points.point(request.latitude(), request.longitude()), request.pinSource(), request.timezone().strip(),
                request.pickupEnabled(), request.sellerDeliveryEnabled(), request.serviceEnabled(),
                request.minimumDeliveryOrder(), request.currency(), clock.now());
        return response(locations.saveAndFlush(location));
    }

    @Transactional
    public void delete(UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.MANAGE_LOCATIONS);
        MerchantLocationEntity location = requireLocation(merchantId, locationId);
        location.delete(clock.now());
        locations.save(location);
    }

    private void validate(MerchantLocationUpsertRequest request) {
        try {
            ZoneId.of(request.timezone().strip());
        } catch (DateTimeException exception) {
            throw validation("Timezone is invalid.");
        }
        if (!"VND".equals(request.currency())) throw validation("V1 location currency must be VND.");
        if (request.minimumDeliveryOrder() != null && request.minimumDeliveryOrder() < 0) {
            throw validation("Minimum delivery order cannot be negative.");
        }
        if (request.locationAreaId() != null
                && !areas.existsByIdAndStatus(request.locationAreaId(), LocationAreaStatus.ACTIVE)) {
            throw validation("Location area must be active.");
        }
    }

    private MerchantLocationEntity requireLocation(UUID merchantId, UUID locationId) {
        return locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(MerchantLocationService::notFound);
    }

    private static MerchantLocationResponse response(MerchantLocationEntity location) {
        return new MerchantLocationResponse(location.getId(), location.getMerchantProfileId(), location.getName(),
                location.getAddressText(), location.getLocationAreaId(), location.getGeoPoint().getY(),
                location.getGeoPoint().getX(), location.getPinSource(), location.getTimezone(),
                location.getLocationStatus(), location.isPickupEnabled(), location.isSellerDeliveryEnabled(),
                location.isServiceEnabled(), location.getMinimumDeliveryOrder(), location.getCurrencyCode(),
                location.getVersion());
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
