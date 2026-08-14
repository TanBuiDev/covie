package vn.covie.catalog.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.api.dto.CatalogItemManagementResponse;
import vn.covie.catalog.api.dto.create.CreateCatalogItemRequest;
import vn.covie.catalog.api.dto.create.LocationOfferCreateRequest;
import vn.covie.catalog.api.dto.create.OptionGroupCreateRequest;
import vn.covie.catalog.api.dto.create.OptionValueCreateRequest;
import vn.covie.catalog.api.dto.create.VariantCreateRequest;
import vn.covie.catalog.model.CatalogItemAttributeValueEntity;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemTagEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.TagEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.persistence.CatalogItemAttributeValueRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemTagRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.TagRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogCreateService {
    public static final String DEFAULT_VARIANT_KEY = "__default__";
    private final CurrentActorProvider actors; private final MerchantAccessPolicy access;
    private final MerchantProfileRepository merchants; private final MerchantLocationRepository locations;
    private final CatalogAggregateValidator validator; private final MediaAttachmentPolicy media;
    private final CatalogItemRepository items; private final CatalogItemAttributeValueRepository attributeValues;
    private final CatalogItemTagRepository itemTags; private final TagRepository tags; private final CatalogItemMediaRepository itemMedia;
    private final OptionGroupRepository optionGroups; private final OptionValueRepository optionValues;
    private final VariantRepository variants; private final VariantOptionValueRepository variantOptions;
    private final LocationVariantRepository locationVariants; private final CatalogSearchDocumentService search;
    private final IdempotencyExecutor idempotency; private final OutboxEventWriter outbox;
    private final UuidV7Generator ids; private final AppClock clock;

    public CatalogCreateService(CurrentActorProvider actors, MerchantAccessPolicy access, MerchantProfileRepository merchants,
            MerchantLocationRepository locations, CatalogAggregateValidator validator, MediaAttachmentPolicy media,
            CatalogItemRepository items, CatalogItemAttributeValueRepository attributeValues, CatalogItemTagRepository itemTags,
            TagRepository tags, CatalogItemMediaRepository itemMedia, OptionGroupRepository optionGroups,
            OptionValueRepository optionValues, VariantRepository variants, VariantOptionValueRepository variantOptions,
            LocationVariantRepository locationVariants, CatalogSearchDocumentService search, IdempotencyExecutor idempotency,
            OutboxEventWriter outbox, UuidV7Generator ids, AppClock clock) {
        this.actors=actors; this.access=access; this.merchants=merchants; this.locations=locations; this.validator=validator; this.media=media;
        this.items=items; this.attributeValues=attributeValues; this.itemTags=itemTags; this.tags=tags; this.itemMedia=itemMedia;
        this.optionGroups=optionGroups; this.optionValues=optionValues; this.variants=variants; this.variantOptions=variantOptions;
        this.locationVariants=locationVariants; this.search=search; this.idempotency=idempotency; this.outbox=outbox; this.ids=ids; this.clock=clock;
    }

    public IdempotencyExecutor.Execution<CatalogItemManagementResponse> create(UUID merchantId, String idempotencyKey,
            CreateCatalogItemRequest request) {
        UUID actorId = actors.currentActor().userId();
        return idempotency.executeWithStatus(actorId, "CATALOG_ITEM_CREATE:" + merchantId, idempotencyKey, request,
                CatalogItemManagementResponse.class, () -> new IdempotencyExecutor.StoredResponse<>(HttpStatus.CREATED.value(),
                        createInTransaction(merchantId, actorId, request)));
    }

    private CatalogItemManagementResponse createInTransaction(UUID merchantId, UUID actorId, CreateCatalogItemRequest request) {
        access.require(merchantId, MerchantCapability.MANAGE_CATALOG_STRUCTURE);
        var merchant = merchants.findActiveForUpdate(merchantId).orElseThrow(CatalogCreateService::notFound);
        if (merchant.getMerchantStatus() != MerchantStatus.ACTIVE) throw reject(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED,
                "Merchant is not active.");
        if (request.type() == CatalogItemType.SERVICE && request.listingKind() == vn.covie.catalog.model.ListingKind.RESALE)
            throw reject(ErrorCode.VALIDATION_FAILED, "Only products can be resale listings.");
        CatalogAggregateValidator.NormalizedAttributes normalized = validator.validate(request.categoryId(), request.type(), request.attributes());
        Instant now = clock.now(); UUID itemId = ids.generate();
        CatalogItemEntity item = items.save(CatalogItemEntity.create(itemId, merchantId, request.type(), request.listingKind(),
                normalized.category().getId(), cleaned(request.name()), slug(request.name(), itemId), cleanNullable(request.description()), now));
        for (CatalogAggregateValidator.NormalizedAttribute value : normalized.values()) attributeValues.save(
                CatalogItemAttributeValueEntity.create(ids.generate(), itemId, value.definition().getId(), value.request().valueText(),
                        value.request().valueNumber(), value.request().valueBoolean(), value.request().optionId()));
        attachTags(item, request.tags(), now);
        attachMedia(item, request.mediaIds(), actorId);
        VariantState variantState = createOptionsAndVariants(item, request.options(), request.variants(), now);
        List<CatalogItemManagementResponse.LocationOfferResponse> offers = createOffers(merchantId, request.type(),
                request.locationOffers(), variantState.variantsByClientKey(), now);
        search.rebuild(itemId);
        outbox.append(new OutboxEventIntent("CATALOG_ITEM_CREATED", "CATALOG_ITEM", itemId,
                Map.of("merchantId", merchantId.toString(), "catalogItemId", itemId.toString())));
        return new CatalogItemManagementResponse(itemId, item.getItemType(), item.getListingKind(), item.getPrimaryCategoryId(),
                item.getName(), item.getDescription(), item.getListingStatus(), variantState.responses(), offers, item.getVersion());
    }

    private VariantState createOptionsAndVariants(CatalogItemEntity item, List<OptionGroupCreateRequest> requestedGroups,
            List<VariantCreateRequest> requestedVariants, Instant now) {
        List<OptionGroupCreateRequest> groups = requestedGroups == null ? List.of() : requestedGroups;
        List<VariantCreateRequest> inputVariants = requestedVariants == null ? List.of() : requestedVariants;
        if (groups.isEmpty() && inputVariants.isEmpty()) {
            VariantEntity variant = variants.save(VariantEntity.createDefault(ids.generate(), item.getId(), "Default", now));
            return new VariantState(Map.of(DEFAULT_VARIANT_KEY, variant), List.of(new CatalogItemManagementResponse.VariantResponse(
                    variant.getId(), null, true, List.of())));
        }
        if (groups.isEmpty() || inputVariants.isEmpty()) throw variantReject("Options and variants must be supplied together.");
        ensureUnique(groups.stream().map(OptionGroupCreateRequest::clientKey).toList(), "Option group clientKey is duplicated.");
        Map<String, UUID> optionGroupByValueKey = new HashMap<>(); Map<String, OptionValueEntity> valueByKey = new HashMap<>();
        for (int groupIndex=0; groupIndex<groups.size(); groupIndex++) {
            OptionGroupCreateRequest groupRequest = groups.get(groupIndex);
            OptionGroupEntity group = optionGroups.save(OptionGroupEntity.create(ids.generate(), item.getId(), cleaned(groupRequest.name()), groupIndex));
            List<OptionValueCreateRequest> values = groupRequest.values() == null ? List.of() : groupRequest.values();
            if (values.isEmpty()) throw variantReject("Option groups must contain values.");
            for (int valueIndex=0; valueIndex<values.size(); valueIndex++) {
                OptionValueCreateRequest valueRequest = values.get(valueIndex);
                if (valueByKey.containsKey(valueRequest.clientKey())) throw variantReject("Option value clientKey is duplicated.");
                OptionValueEntity value = optionValues.save(OptionValueEntity.create(ids.generate(), group.getId(), cleaned(valueRequest.value()), valueIndex));
                valueByKey.put(valueRequest.clientKey(), value); optionGroupByValueKey.put(valueRequest.clientKey(), group.getId());
            }
        }
        ensureUnique(inputVariants.stream().map(VariantCreateRequest::clientKey).toList(), "Variant clientKey is duplicated.");
        Set<String> combinations = new HashSet<>(); Map<String, VariantEntity> byClientKey = new HashMap<>();
        List<CatalogItemManagementResponse.VariantResponse> responses = new ArrayList<>();
        for (int variantIndex=0; variantIndex<inputVariants.size(); variantIndex++) {
            VariantCreateRequest request = inputVariants.get(variantIndex);
            List<String> keys = request.optionValueKeys() == null ? List.of() : request.optionValueKeys();
            if (keys.size() != groups.size()) throw variantReject("Every variant must select one value from each option group.");
            Set<UUID> selectedGroups = new HashSet<>(); List<OptionValueEntity> selected = new ArrayList<>();
            for (String key : keys) {
                OptionValueEntity value = valueByKey.get(key); if (value == null) throw variantReject("Variant references an unknown option value.");
                if (!selectedGroups.add(optionGroupByValueKey.get(key))) throw variantReject("Variant selects two values from one option group.");
                selected.add(value);
            }
            String combination = selected.stream().map(OptionValueEntity::getId).map(UUID::toString).sorted().reduce((a,b)->a+":"+b).orElse("");
            if (!combinations.add(combination)) throw variantReject("Duplicate variant combination.");
            VariantEntity variant = variants.save(VariantEntity.create(ids.generate(), item.getId(), cleanNullable(request.sku()), cleaned(request.name()), false, variantIndex, now));
            for (OptionValueEntity value : selected) variantOptions.save(VariantOptionValueEntity.associate(variant, value));
            byClientKey.put(request.clientKey(), variant);
            responses.add(new CatalogItemManagementResponse.VariantResponse(variant.getId(), variant.getVariantName(), false,
                    selected.stream().map(OptionValueEntity::getId).toList()));
        }
        return new VariantState(byClientKey, responses);
    }

    private List<CatalogItemManagementResponse.LocationOfferResponse> createOffers(UUID merchantId, CatalogItemType type,
            List<LocationOfferCreateRequest> requested, Map<String, VariantEntity> variantsByClientKey, Instant now) {
        List<CatalogItemManagementResponse.LocationOfferResponse> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (LocationOfferCreateRequest offer : requested) {
            VariantEntity variant = variantsByClientKey.get(offer.variantClientKey());
            if (variant == null) throw reject(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, "Location offer references an unknown variant.");
            MerchantLocationEntity location = locations.findActiveForUpdate(offer.merchantLocationId(), merchantId).orElseThrow(CatalogCreateService::notFound);
            validateOfferType(type, location, offer);
            if (!seen.add(location.getId() + ":" + variant.getId())) throw reject(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, "Duplicate location offer.");
            LocationVariantEntity persisted = locationVariants.save(LocationVariantEntity.create(ids.generate(), location.getId(), variant.getId(),
                    offer.price(), offer.currency(), offer.available(), offer.inventoryMode(), offer.preparationTimeMinutes(), now));
            result.add(new CatalogItemManagementResponse.LocationOfferResponse(persisted.getId(), location.getId(), variant.getId(),
                    persisted.getPrice(), persisted.getCurrencyCode(), persisted.isAvailable(), persisted.getInventoryMode(), persisted.getPreparationTimeMinutes()));
        }
        return result;
    }
    private void validateOfferType(CatalogItemType type, MerchantLocationEntity location, LocationOfferCreateRequest offer) {
        if (type == CatalogItemType.PRODUCT && offer.inventoryMode() == null) rejectOffer("Product offer requires TRACKED or UNTRACKED inventory mode.");
        if (type == CatalogItemType.SERVICE && offer.inventoryMode() != null) rejectOffer("Service offer must not define inventory mode.");
        if (type == CatalogItemType.SERVICE && !location.isServiceEnabled()) rejectOffer("Merchant location is not enabled for services.");
        if (!"VND".equals(offer.currency())) rejectOffer("V1 location offer currency must be VND.");
    }
    private void attachTags(CatalogItemEntity item, List<String> requested, Instant now) {
        Set<String> normalized = new HashSet<>(); for (String raw : requested == null ? List.<String>of() : requested) {
            String name=cleaned(raw); String key=name.toLowerCase(Locale.ROOT); if (!normalized.add(key)) continue;
            TagEntity tag=tags.findByNormalizedName(key).orElseGet(() -> tags.save(TagEntity.create(ids.generate(), name, key, now)));
            itemTags.save(CatalogItemTagEntity.associate(item, tag));
        }
    }
    private void attachMedia(CatalogItemEntity item, List<UUID> mediaIds, UUID actorId) {
        Set<UUID> unique = new HashSet<>(); int order=0; for (UUID mediaId : mediaIds == null ? List.<UUID>of() : mediaIds) {
            if (!unique.add(mediaId)) continue; MediaAssetEntity asset=media.requireReadyOwned(mediaId, actorId, Set.of(MediaPurpose.CATALOG_IMAGE));
            itemMedia.save(CatalogItemMediaEntity.image(item, asset, order, order++ == 0));
        }
    }
    private static void ensureUnique(List<String> keys, String message) { if (new HashSet<>(keys).size()!=keys.size()) throw variantReject(message); }
    private static String cleaned(String value) { if (value==null || value.strip().isEmpty()) throw reject(ErrorCode.VALIDATION_FAILED, "Value must not be blank."); return value.strip(); }
    private static String cleanNullable(String value) { return value==null || value.strip().isEmpty() ? null : value.strip(); }
    private static String slug(String value, UUID id) { return cleaned(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("(^-|-$)", "") + "-" + id; }
    private static ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."); }
    private static ApiException variantReject(String message) { return reject(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID, message); }
    private static ApiException reject(ErrorCode code, String message) { return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private static void rejectOffer(String message) { throw reject(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, message); }
    private record VariantState(Map<String, VariantEntity> variantsByClientKey, List<CatalogItemManagementResponse.VariantResponse> responses) {}
}
