package vn.covie.delivery.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.delivery.api.dto.DeliveryCoverageResponse;
import vn.covie.delivery.api.dto.DeliveryCoverageUpsertRequest;
import vn.covie.delivery.model.CoverageStatus;
import vn.covie.delivery.model.CoverageType;
import vn.covie.delivery.model.DeliveryCoverageAreaEntity;
import vn.covie.delivery.model.DeliveryCoverageEntity;
import vn.covie.delivery.persistence.DeliveryCoverageAreaRepository;
import vn.covie.delivery.persistence.DeliveryCoverageRepository;
import vn.covie.location.model.LocationAreaStatus;
import vn.covie.location.persistence.LocationAreaRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class DeliveryCoverageService {
    private final MerchantAccessPolicy access;
    private final MerchantLocationRepository locations;
    private final DeliveryCoverageRepository coverages;
    private final DeliveryCoverageAreaRepository areas;
    private final LocationAreaRepository locationAreas;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public DeliveryCoverageService(MerchantAccessPolicy access, MerchantLocationRepository locations,
            DeliveryCoverageRepository coverages, DeliveryCoverageAreaRepository areas,
            LocationAreaRepository locationAreas, UuidV7Generator ids, AppClock clock) {
        this.access = access; this.locations = locations; this.coverages = coverages; this.areas = areas;
        this.locationAreas = locationAreas; this.ids = ids; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryCoverageResponse> list(UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.MANAGE_LOCATION_OFFER);
        return coverages.findByMerchantLocationIdOrderByCoverageType(locationId).stream().map(this::response).toList();
    }

    @Transactional
    public DeliveryCoverageResponse create(UUID merchantId, UUID locationId, DeliveryCoverageUpsertRequest request) {
        MerchantLocationEntity location = requireLocation(merchantId, locationId);
        validate(location, request);
        CoverageStatus status = request.status() == null ? CoverageStatus.ACTIVE : request.status();
        if (status == CoverageStatus.ACTIVE && coverages.findFirstByMerchantLocationIdAndCoverageTypeAndCoverageStatus(
                locationId, request.coverageType(), CoverageStatus.ACTIVE).isPresent()) {
            throw error(ErrorCode.DELIVERY_COVERAGE_DUPLICATE_ACTIVE, HttpStatus.CONFLICT, "An active coverage already exists.");
        }
        DeliveryCoverageEntity coverage = DeliveryCoverageEntity.create(ids.generate(), locationId, request.coverageType(),
                request.radiusEnabled(), request.radiusMeters(), request.areaEnabled(), status, clock.now());
        validateAreas(request.areaEnabled(), request.locationAreaIds());
        DeliveryCoverageEntity saved = coverages.saveAndFlush(coverage);
        replaceAreas(saved.getId(), request.areaEnabled() ? request.locationAreaIds() : Set.of());
        return response(saved);
    }

    @Transactional
    public DeliveryCoverageResponse update(UUID merchantId, UUID locationId, UUID coverageId, String ifMatch,
            DeliveryCoverageUpsertRequest request) {
        MerchantLocationEntity location = requireLocation(merchantId, locationId);
        DeliveryCoverageEntity coverage = coverages.findForUpdate(coverageId, locationId).orElseThrow(DeliveryCoverageService::notFound);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), coverage.getVersion());
        if (request.coverageType() != coverage.getCoverageType()) throw invalid("Coverage type cannot be changed.");
        validate(location, request);
        CoverageStatus status = request.status() == null ? coverage.getCoverageStatus() : request.status();
        if (status == CoverageStatus.ACTIVE && coverage.getCoverageStatus() != CoverageStatus.ACTIVE
                && coverages.findFirstByMerchantLocationIdAndCoverageTypeAndCoverageStatus(locationId, coverage.getCoverageType(), CoverageStatus.ACTIVE).isPresent()) {
            throw error(ErrorCode.DELIVERY_COVERAGE_DUPLICATE_ACTIVE, HttpStatus.CONFLICT, "An active coverage already exists.");
        }
        validateAreas(request.areaEnabled(), request.locationAreaIds());
        coverage.update(request.radiusEnabled(), request.radiusMeters(), request.areaEnabled(), status, clock.now());
        DeliveryCoverageEntity saved = coverages.saveAndFlush(coverage);
        replaceAreas(saved.getId(), request.areaEnabled() ? request.locationAreaIds() : Set.of());
        return response(saved);
    }

    @Transactional
    public void delete(UUID merchantId, UUID locationId, UUID coverageId, String ifMatch) {
        requireLocation(merchantId, locationId);
        DeliveryCoverageEntity coverage = coverages.findForUpdate(coverageId, locationId).orElseThrow(DeliveryCoverageService::notFound);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), coverage.getVersion());
        coverage.update(coverage.isRadiusEnabled(), coverage.getRadiusMeters(), coverage.isAreaEnabled(), CoverageStatus.INACTIVE, clock.now());
        coverages.save(coverage);
    }

    private MerchantLocationEntity requireLocation(UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.MANAGE_LOCATION_OFFER);
        return locations.findActiveForUpdate(locationId, merchantId).orElseThrow(DeliveryCoverageService::notFound);
    }

    private void validate(MerchantLocationEntity location, DeliveryCoverageUpsertRequest request) {
        if (request == null || request.coverageType() == null) throw invalid("Coverage type is required.");
        if (!request.radiusEnabled() && request.radiusMeters() != null) throw invalid("Radius must be omitted when disabled.");
        if (request.radiusEnabled() && (request.radiusMeters() == null || request.radiusMeters() <= 0)) throw invalid("Radius must be positive.");
        if (request.areaEnabled() && (request.locationAreaIds() == null || request.locationAreaIds().isEmpty())) throw invalid("At least one area is required.");
        if (!request.areaEnabled() && request.locationAreaIds() != null && !request.locationAreaIds().isEmpty()) throw invalid("Areas require area mode.");
        CoverageStatus status = request.status() == null ? CoverageStatus.ACTIVE : request.status();
        if (status == CoverageStatus.ACTIVE && request.coverageType() == CoverageType.PRODUCT_DELIVERY && !location.isSellerDeliveryEnabled()) {
            throw error(ErrorCode.DELIVERY_COVERAGE_LOCATION_NOT_ENABLED, HttpStatus.UNPROCESSABLE_ENTITY, "Seller delivery is disabled.");
        }
        if (status == CoverageStatus.ACTIVE && request.coverageType() == CoverageType.SERVICE_AREA && !location.isServiceEnabled()) {
            throw error(ErrorCode.DELIVERY_COVERAGE_LOCATION_NOT_ENABLED, HttpStatus.UNPROCESSABLE_ENTITY, "Service is disabled.");
        }
        if (!request.radiusEnabled() && !request.areaEnabled()) throw invalid("At least one coverage mode is required.");
    }

    private void validateAreas(boolean enabled, Set<UUID> ids) {
        if (!enabled) return;
        for (UUID id : ids) if (!locationAreas.existsByIdAndStatus(id, LocationAreaStatus.ACTIVE)) throw invalid("Location area must be active.");
    }

    private void replaceAreas(UUID coverageId, Set<UUID> ids) {
        areas.deleteAll(areas.findByIdDeliveryCoverageId(coverageId));
        if (!ids.isEmpty()) areas.saveAll(ids.stream().map(id -> DeliveryCoverageAreaEntity.link(coverageId, id)).toList());
    }

    private DeliveryCoverageResponse response(DeliveryCoverageEntity coverage) {
        Set<UUID> selected = areas.findByIdDeliveryCoverageId(coverage.getId()).stream().map(a -> a.getId().getLocationAreaId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new DeliveryCoverageResponse(coverage.getId(), coverage.getMerchantLocationId(), coverage.getCoverageType(), coverage.isRadiusEnabled(), coverage.getRadiusMeters(), coverage.isAreaEnabled(), selected, coverage.getCoverageStatus(), coverage.getVersion());
    }
    private static ApiException invalid(String message) { return error(ErrorCode.DELIVERY_COVERAGE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private static ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."); }
    private static ApiException error(ErrorCode code, HttpStatus status, String message) { return new ApiException(code, status, message); }
}
