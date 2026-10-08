package vn.covie.needs.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.needs.api.dto.CreateNeedResponseRequest;
import vn.covie.needs.api.dto.NeedResponseItemResponse;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.api.dto.ReplaceNeedResponseRequest;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedResponseItemStatus;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedResponseService {
    private final CurrentActorProvider actors;
    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final NeedResponseItemRepository responseItems;
    private final MerchantLocationRepository locations;
    private final MerchantAccessPolicy merchantAccess;
    private final NeedResponseEligibilityPolicy eligibility;
    private final NeedResponseItemFactory itemFactory;
    private final IdempotencyExecutor idempotency;
    private final OutboxEventWriter outbox;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public NeedResponseService(CurrentActorProvider actors, NeedRequestRepository needs,
            NeedResponseRepository responses, NeedResponseItemRepository responseItems,
            MerchantLocationRepository locations, MerchantAccessPolicy merchantAccess,
            NeedResponseEligibilityPolicy eligibility, NeedResponseItemFactory itemFactory,
            IdempotencyExecutor idempotency, OutboxEventWriter outbox, UuidV7Generator ids, AppClock clock) {
        this.actors = actors; this.needs = needs; this.responses = responses; this.responseItems = responseItems;
        this.locations = locations; this.merchantAccess = merchantAccess; this.eligibility = eligibility;
        this.itemFactory = itemFactory; this.idempotency = idempotency; this.outbox = outbox;
        this.ids = ids; this.clock = clock;
    }

    public IdempotencyExecutor.Execution<NeedResponseResponse> create(
            UUID needId, String key, CreateNeedResponseRequest request) {
        UUID actorId = actors.currentActor().userId();
        return idempotency.executeWithStatus(actorId, "need.response.create:" + needId, key, request,
                NeedResponseResponse.class,
                () -> new IdempotencyExecutor.StoredResponse<>(HttpStatus.CREATED.value(),
                        createInTransaction(needId, actorId, request)));
    }

    @Transactional
    protected NeedResponseResponse createInTransaction(UUID needId, UUID actorId, CreateNeedResponseRequest request) {
        NeedRequestEntity need = lockNeed(needId);
        Instant now = clock.now();
        MerchantLocationEntity location = resolveAuthorizedLocation(request.merchantLocationId());
        UUID merchantId = location.getMerchantProfileId();
        if (responses.findByNeedRequestIdAndMerchantLocationIdAndResponseStatus(
                needId, location.getId(), NeedResponseStatus.ACTIVE).isPresent()) {
            throw conflict(ErrorCode.NEED_RESPONSE_ALREADY_ACTIVE, "An active response already exists for this location.");
        }
        var eligibleItems = eligibility.validate(need, merchantId, location.getId(), request.locationVariantIds(), now);
        NeedResponseEntity response = responses.save(NeedResponseEntity.active(ids.generate(), needId, merchantId,
                location.getId(), actorId, request.note(), now));
        List<NeedResponseItemEntity> items = eligibleItems.stream()
                .map(item -> itemFactory.active(response.getId(), item, now)).toList();
        responseItems.saveAll(items);
        try {
            responses.flush();
            responseItems.flush();
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ErrorCode.NEED_RESPONSE_ALREADY_ACTIVE,
                    "An active response already exists for this location.");
        }
        outbox.append(new OutboxEventIntent("NEED_RESPONSE_CREATED", "NEED_REQUEST", needId, Map.of(
                "needRequestId", needId.toString(), "responseId", response.getId().toString(),
                "merchantId", merchantId.toString(), "merchantLocationId", location.getId().toString())));
        return writeDto(response, items);
    }

    @Transactional
    public NeedResponseResponse replace(UUID needId, UUID responseId, String ifMatch,
            ReplaceNeedResponseRequest request) {
        NeedRequestEntity need = lockNeed(needId);
        NeedResponseEntity response = lockResponse(needId, responseId);
        authorizeResponse(response);
        requireVersion(response, ifMatch);
        Instant now = clock.now();
        if (!need.isActiveAt(now)) throw conflict(ErrorCode.NEED_NOT_ACTIVE, "Need is not active.");
        if (response.getResponseStatus() != NeedResponseStatus.ACTIVE) {
            throw conflict(ErrorCode.NEED_RESPONSE_STATE_CONFLICT, "Need response is not active.");
        }
        var eligibleItems = eligibility.validate(need, response.getMerchantProfileId(),
                response.getMerchantLocationId(), request.locationVariantIds(), now);
        List<NeedResponseItemEntity> active = responseItems.findByNeedResponseIdAndItemStatusOrderByCreatedAtAsc(
                responseId, NeedResponseItemStatus.ACTIVE);
        active.forEach(item -> item.retire(now));
        response.replaceNote(request.note(), now);
        List<NeedResponseItemEntity> replacements = eligibleItems.stream()
                .map(item -> itemFactory.active(responseId, item, now)).toList();
        responseItems.saveAll(replacements);
        responses.flush();
        responseItems.flush();
        outbox.append(new OutboxEventIntent("NEED_RESPONSE_UPDATED", "NEED_REQUEST", needId, Map.of(
                "needRequestId", needId.toString(), "responseId", responseId.toString(),
                "version", response.getVersion())));
        return writeDto(response, replacements);
    }

    public IdempotencyExecutor.Execution<NeedResponseResponse> withdraw(
            UUID needId, UUID responseId, String key) {
        UUID actorId = actors.currentActor().userId();
        Map<String, String> fingerprint = Map.of("responseId", responseId.toString());
        return idempotency.executeWithStatus(actorId, "need.response.withdraw:" + responseId, key, fingerprint,
                NeedResponseResponse.class,
                () -> new IdempotencyExecutor.StoredResponse<>(HttpStatus.OK.value(),
                        withdrawInTransaction(needId, responseId)));
    }

    @Transactional
    protected NeedResponseResponse withdrawInTransaction(UUID needId, UUID responseId) {
        NeedRequestEntity need = lockNeed(needId);
        NeedResponseEntity response = lockResponse(needId, responseId);
        authorizeResponse(response);
        Instant now = clock.now();
        if (!need.isActiveAt(now)) throw conflict(ErrorCode.NEED_NOT_ACTIVE, "Need is not active.");
        if (response.getResponseStatus() != NeedResponseStatus.ACTIVE) {
            throw conflict(ErrorCode.NEED_RESPONSE_STATE_CONFLICT, "Need response is not active.");
        }
        response.withdraw(now);
        responses.flush();
        outbox.append(new OutboxEventIntent("NEED_RESPONSE_WITHDRAWN", "NEED_REQUEST", needId, Map.of(
                "needRequestId", needId.toString(), "responseId", responseId.toString(),
                "merchantId", response.getMerchantProfileId().toString(),
                "merchantLocationId", response.getMerchantLocationId().toString())));
        List<NeedResponseItemEntity> items = responseItems.findByNeedResponseIdAndItemStatusOrderByCreatedAtAsc(
                responseId, NeedResponseItemStatus.ACTIVE);
        return writeDto(response, items);
    }

    private NeedRequestEntity lockNeed(UUID needId) {
        return needs.lockById(needId).orElseThrow(NeedResponseService::notFound);
    }

    private NeedResponseEntity lockResponse(UUID needId, UUID responseId) {
        return responses.lockByNeedAndId(needId, responseId).orElseThrow(NeedResponseService::notFound);
    }

    private MerchantLocationEntity resolveAuthorizedLocation(UUID locationId) {
        MerchantLocationEntity location = locations.findById(locationId)
                .filter(v -> v.getDeletedAt() == null).orElseThrow(NeedResponseService::notFound);
        merchantAccess.requireLocation(location.getMerchantProfileId(), location.getId(),
                MerchantCapability.RESPOND_TO_NEEDS);
        return location;
    }

    private void authorizeResponse(NeedResponseEntity response) {
        merchantAccess.requireLocation(response.getMerchantProfileId(), response.getMerchantLocationId(),
                MerchantCapability.RESPOND_TO_NEEDS);
    }

    private static void requireVersion(NeedResponseEntity response, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(ErrorCode.IF_MATCH_REQUIRED, HttpStatus.PRECONDITION_REQUIRED,
                    "If-Match is required.");
        }
        String expected = "\"v" + response.getVersion() + "\"";
        if (!expected.equals(ifMatch.strip())) {
            if (!ifMatch.strip().matches("\"v\\d+\"")) {
                throw new ApiException(ErrorCode.INVALID_IF_MATCH, HttpStatus.BAD_REQUEST, "Invalid If-Match value.");
            }
            throw conflict(ErrorCode.RESOURCE_VERSION_CONFLICT, "Resource version changed.");
        }
    }

    private static NeedResponseResponse writeDto(NeedResponseEntity response, List<NeedResponseItemEntity> items) {
        return new NeedResponseResponse(response.getId(), response.getNeedRequestId(), response.getMerchantProfileId(),
                null, response.getMerchantLocationId(), null, null, response.getResponseStatus(), response.getNote(),
                response.getVersion(), response.getCreatedAt(), items.stream().map(item -> new NeedResponseItemResponse(
                        item.getId(), item.getLocationVariantId(), item.getCatalogItemId(), null, item.getVariantId(),
                        new NeedResponseItemResponse.MoneyResponse(item.getPriceSnapshot(), item.getCurrencyCode()),
                        null, item.getItemStatus() == NeedResponseItemStatus.ACTIVE,
                        item.getItemStatus() == NeedResponseItemStatus.ACTIVE)).toList());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
    private static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.CONFLICT, message);
    }
}
