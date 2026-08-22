package vn.covie.needs.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationContextResolver;
import vn.covie.needs.api.dto.CreateNeedRequest;
import vn.covie.needs.api.dto.NeedDetailResponse;
import vn.covie.needs.api.dto.NeedLocationSummaryResponse;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.CategoryHierarchyRepository;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedCommandService {
    private final CurrentActorProvider actors;
    private final LocationContextResolver locations;
    private final CategoryHierarchyRepository categories;
    private final NeedLocationSnapshotFactory snapshots;
    private final NeedRequestRepository needs;
    private final IdempotencyExecutor idempotency;
    private final OutboxEventWriter outbox;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public NeedCommandService(CurrentActorProvider actors, LocationContextResolver locations,
            CategoryHierarchyRepository categories, NeedLocationSnapshotFactory snapshots, NeedRequestRepository needs,
            IdempotencyExecutor idempotency, OutboxEventWriter outbox, UuidV7Generator ids, AppClock clock) {
        this.actors = actors; this.locations = locations; this.categories = categories; this.snapshots = snapshots;
        this.needs = needs; this.idempotency = idempotency; this.outbox = outbox; this.ids = ids; this.clock = clock;
    }

    public IdempotencyExecutor.Execution<NeedDetailResponse> create(String key, CreateNeedRequest request) {
        UUID buyerId = actors.currentActor().userId();
        return idempotency.executeWithStatus(buyerId, "need.create", key, request, NeedDetailResponse.class,
                () -> new IdempotencyExecutor.StoredResponse<>(HttpStatus.CREATED.value(), createInTransaction(buyerId, request)));
    }

    @Transactional
    protected NeedDetailResponse createInTransaction(UUID buyerId, CreateNeedRequest request) {
        Instant now = clock.now();
        if (!request.expiresAt().isAfter(now)) throw invalid(ErrorCode.NEED_EXPIRY_INVALID, "Need expiry must be in the future.");
        if (request.type() == NeedType.SERVICE && request.requestedQuantity() != null && request.requestedQuantity() != 1) {
            throw invalid(ErrorCode.VALIDATION_FAILED, "Service need quantity must be one when provided.");
        }
        if (!categories.supportsNeedType(request.categoryId(), request.type())) {
            throw invalid(ErrorCode.NEED_CATEGORY_TYPE_MISMATCH, "Category is not compatible with need type.");
        }
        LocationContext location;
        try {
            location = locations.resolveForDiscovery(request.lat(), request.lng(), request.addressId());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid(ErrorCode.NEED_LOCATION_INVALID, "Need location is invalid.");
        }
        NeedRequestEntity need = needs.save(NeedRequestEntity.create(ids.generate(), buyerId, request.type(), request.categoryId(),
                request.title(), request.queryText(), request.description(), request.requestedQuantity(), location.locationAreaId(),
                location.point(), snapshots.snapshot(location), request.radiusMeters(), request.expiresAt(), now));
        outbox.append(new OutboxEventIntent("NEED_CREATED", "NEED_REQUEST", need.getId(), Map.of(
                "needRequestId", need.getId().toString(), "buyerUserId", buyerId.toString(),
                "needType", need.getNeedType().name(), "categoryId", need.getCategoryId().toString())));
        return detail(need, now, 0);
    }

    public IdempotencyExecutor.Execution<NeedDetailResponse> cancel(UUID needId, String key) {
        UUID buyerId = actors.currentActor().userId();
        return idempotency.executeWithStatus(buyerId, "need.cancel:" + needId, key, Map.of(), NeedDetailResponse.class,
                () -> new IdempotencyExecutor.StoredResponse<>(HttpStatus.OK.value(), cancelInTransaction(needId, buyerId)));
    }

    @Transactional
    protected NeedDetailResponse cancelInTransaction(UUID needId, UUID buyerId) {
        NeedRequestEntity need = needs.lockByIdAndBuyerUserId(needId, buyerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        Instant now = clock.now();
        if (!need.isActiveAt(now)) throw new ApiException(ErrorCode.NEED_STATE_CONFLICT, HttpStatus.CONFLICT,
                "Need is not active.", Map.of("currentStatus", effectiveStatus(need, now).name()));
        need.cancel(now);
        outbox.append(new OutboxEventIntent("NEED_CANCELLED", "NEED_REQUEST", need.getId(), Map.of(
                "needRequestId", need.getId().toString(), "buyerUserId", buyerId.toString())));
        return detail(need, now, 0);
    }

    static NeedDetailResponse detail(NeedRequestEntity need, Instant now, long responseCount) {
        return new NeedDetailResponse(need.getId(), need.getNeedType(), effectiveStatus(need, now), need.getCategoryId(),
                need.getTitle(), need.getQueryText(), need.getDescription(), need.getRequestedQuantity(),
                new NeedLocationSummaryResponse(parseUuid(need, "addressId"), need.getLocationAreaId(),
                        need.getLocationSnapshot().path("timezone").asText("UTC")),
                need.getSearchRadiusMeters(), need.getExpiresAt(), responseCount, need.getFulfilledOrderId(), need.getCreatedAt(), need.getClosedAt());
    }

    static NeedStatus effectiveStatus(NeedRequestEntity need, Instant now) {
        return need.getRequestStatus() == NeedStatus.ACTIVE && !need.getExpiresAt().isAfter(now) ? NeedStatus.EXPIRED : need.getRequestStatus();
    }

    private static UUID parseUuid(NeedRequestEntity need, String field) {
        String value = need.getLocationSnapshot().path(field).asText(null);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static ApiException invalid(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
