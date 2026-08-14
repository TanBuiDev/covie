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
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.api.dto.create.AttributeValueRequest;
import vn.covie.catalog.api.dto.edit.CreateVariantRequest;
import vn.covie.catalog.api.dto.edit.UpdateCatalogItemRequest;
import vn.covie.catalog.api.dto.edit.UpdateLocationOfferRequest;
import vn.covie.catalog.api.dto.edit.UpdateVariantRequest;
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
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemAttributeValueRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemTagRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
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
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Applies changes to exactly one catalog resource; no endpoint replaces a catalog aggregate. */
@Service
public class CatalogEditService {
    private final CurrentActorProvider actors; private final MerchantAccessPolicy access;
    private final CatalogItemRepository items; private final CatalogItemAttributeValueRepository attributes;
    private final CatalogItemTagRepository itemTags; private final TagRepository tags; private final CatalogItemMediaRepository itemMedia;
    private final CategoryAttributeRepository categoryAttributes; private final CatalogAggregateValidator validator;
    private final VariantRepository variants; private final VariantOptionValueRepository variantOptions;
    private final OptionGroupRepository optionGroups; private final OptionValueRepository optionValues;
    private final LocationVariantRepository locationVariants; private final MerchantLocationRepository locations;
    private final MediaAttachmentPolicy media; private final CatalogSearchDocumentService search;
    private final UuidV7Generator ids; private final AppClock clock;

    public CatalogEditService(CurrentActorProvider actors, MerchantAccessPolicy access, CatalogItemRepository items,
            CatalogItemAttributeValueRepository attributes, CatalogItemTagRepository itemTags, TagRepository tags,
            CatalogItemMediaRepository itemMedia, CategoryAttributeRepository categoryAttributes,
            CatalogAggregateValidator validator, VariantRepository variants, VariantOptionValueRepository variantOptions,
            OptionGroupRepository optionGroups, OptionValueRepository optionValues, LocationVariantRepository locationVariants,
            MerchantLocationRepository locations, MediaAttachmentPolicy media, CatalogSearchDocumentService search,
            UuidV7Generator ids, AppClock clock) {
        this.actors=actors; this.access=access; this.items=items; this.attributes=attributes; this.itemTags=itemTags; this.tags=tags;
        this.itemMedia=itemMedia; this.categoryAttributes=categoryAttributes; this.validator=validator; this.variants=variants;
        this.variantOptions=variantOptions; this.optionGroups=optionGroups; this.optionValues=optionValues;
        this.locationVariants=locationVariants; this.locations=locations; this.media=media; this.search=search; this.ids=ids; this.clock=clock;
    }

    @Transactional
    public EditResult updateItem(UUID merchantId, UUID itemId, String ifMatch, UpdateCatalogItemRequest request) {
        access.require(merchantId, MerchantCapability.MANAGE_CATALOG_STRUCTURE);
        CatalogItemEntity item = item(merchantId, itemId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), item.getVersion());
        UUID categoryId = request.primaryCategoryId() == null ? item.getPrimaryCategoryId() : request.primaryCategoryId();
        boolean categoryChanged = !categoryId.equals(item.getPrimaryCategoryId());
        boolean replacingAttributes = request.attributes() != null || categoryChanged;
        CatalogAggregateValidator.NormalizedAttributes normalized = replacingAttributes
                ? validator.validate(categoryId, item.getItemType(), request.attributes() == null ? currentAttributes(item) : request.attributes())
                : null;
        Instant now = clock.now();
        String name = request.name() == null ? item.getName() : required(request.name());
        String description = request.description() == null ? item.getDescription() : nullable(request.description());
        item.update(categoryId, name, description, now);
        if (replacingAttributes) replaceAttributes(item, normalized.values());
        if (request.tags() != null) replaceTags(item, request.tags(), now);
        if (request.mediaIds() != null) replaceMedia(item, request.mediaIds(), actors.currentActor().userId());
        CatalogItemEntity saved = items.saveAndFlush(item);
        if (request.name() != null || request.description() != null || categoryChanged || request.tags() != null) search.rebuild(itemId);
        return new EditResult(saved.getId(), saved.getVersion());
    }

    @Transactional
    public EditResult updateVariant(UUID merchantId, UUID itemId, UUID variantId, String ifMatch, UpdateVariantRequest request) {
        access.require(merchantId, MerchantCapability.MANAGE_CATALOG_STRUCTURE);
        item(merchantId, itemId);
        VariantEntity variant = variant(itemId, variantId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), variant.getVersion());
        VariantStatus status = request.variantStatus() == null ? variant.getVariantStatus() : request.variantStatus();
        if (status == VariantStatus.DELETED) throw variantReject("Use DELETE to archive a variant.");
        VariantEntity saved = variants.saveAndFlush(updateVariant(variant, request.sku(), request.variantName(), status));
        return new EditResult(saved.getId(), saved.getVersion());
    }

    @Transactional
    public EditResult createVariant(UUID merchantId, UUID itemId, CreateVariantRequest request) {
        access.require(merchantId, MerchantCapability.MANAGE_CATALOG_STRUCTURE);
        lockedItem(merchantId, itemId);
        List<OptionGroupEntity> groups = optionGroups.findAllByCatalogItemIdOrderBySortOrderAscIdAsc(itemId);
        List<UUID> selectedIds = request.optionValueIds() == null ? List.of() : request.optionValueIds();
        if (new HashSet<>(selectedIds).size() != selectedIds.size() || selectedIds.size() != groups.size()) {
            throw variantReject("Every variant must select one value from each option group.");
        }
        Map<UUID, OptionValueEntity> values = valuesFor(groups);
        Set<UUID> groupIds = new HashSet<>(); List<OptionValueEntity> selected = new ArrayList<>();
        for (UUID valueId : selectedIds) {
            OptionValueEntity value = values.get(valueId);
            if (value == null || !groupIds.add(value.getOptionGroupId())) throw variantReject("Variant references an invalid option combination.");
            selected.add(value);
        }
        if (groupIds.size() != groups.size() || hasCombination(itemId, selectedIds)) throw variantReject("Duplicate variant combination.");
        int sortOrder = variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId).stream()
                .mapToInt(VariantEntity::getSortOrder).max().orElse(-1) + 1;
        VariantEntity variant = variants.save(VariantEntity.create(ids.generate(), itemId, nullable(request.sku()), required(request.variantName()), false, sortOrder, clock.now()));
        for (OptionValueEntity value : selected) variantOptions.save(VariantOptionValueEntity.associate(variant, value));
        VariantEntity saved = variants.saveAndFlush(variant);
        return new EditResult(saved.getId(), saved.getVersion());
    }

    @Transactional
    public EditResult deleteVariant(UUID merchantId, UUID itemId, UUID variantId, String ifMatch) {
        access.require(merchantId, MerchantCapability.MANAGE_CATALOG_STRUCTURE);
        lockedItem(merchantId, itemId);
        VariantEntity variant = variant(itemId, variantId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), variant.getVersion());
        if (variants.countByCatalogItemIdAndDeletedAtIsNull(itemId) <= 1) throw variantReject("The final non-deleted variant cannot be deleted.");
        variant.delete(clock.now());
        VariantEntity saved = variants.saveAndFlush(variant);
        return new EditResult(saved.getId(), saved.getVersion());
    }

    @Transactional
    public EditResult updateLocationOffer(UUID merchantId, UUID itemId, UUID offerId, String ifMatch,
            UpdateLocationOfferRequest request) {
        CatalogItemEntity item = item(merchantId, itemId);
        LocationVariantEntity offer = locationVariants.findUsableById(offerId).orElseThrow(CatalogEditService::notFound);
        VariantEntity variant = variants.findByIdAndCatalogItemIdAndDeletedAtIsNull(offer.getVariantId(), itemId)
                .orElseThrow(CatalogEditService::notFound);
        if (!variant.getId().equals(offer.getVariantId())) throw notFound();
        access.requireLocation(merchantId, offer.getMerchantLocationId(), MerchantCapability.MANAGE_LOCATION_OFFER);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), offer.getVersion());
        long price = request.price() == null ? offer.getPrice() : request.price();
        boolean available = request.available() == null ? offer.isAvailable() : request.available();
        InventoryMode inventoryMode = request.inventoryMode() == null ? offer.getInventoryMode() : request.inventoryMode();
        Integer preparation = request.preparationTimeMinutes() == null ? offer.getPreparationTimeMinutes() : request.preparationTimeMinutes();
        MerchantLocationEntity location = locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(offer.getMerchantLocationId(), merchantId)
                .orElseThrow(CatalogEditService::notFound);
        validateOffer(item.getItemType(), location, inventoryMode);
        offer.update(price, available, inventoryMode, preparation, clock.now());
        LocationVariantEntity saved = locationVariants.saveAndFlush(offer);
        return new EditResult(saved.getId(), saved.getVersion());
    }

    private CatalogItemEntity item(UUID merchantId, UUID itemId) { return items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId).orElseThrow(CatalogEditService::notFound); }
    private CatalogItemEntity lockedItem(UUID merchantId, UUID itemId) { return items.findLockedByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId).orElseThrow(CatalogEditService::notFound); }
    private VariantEntity variant(UUID itemId, UUID variantId) { return variants.findByIdAndCatalogItemIdAndDeletedAtIsNull(variantId, itemId).orElseThrow(CatalogEditService::notFound); }
    private VariantEntity updateVariant(VariantEntity variant, String sku, String name, VariantStatus status) { variant.update(sku == null ? variant.getSku() : nullable(sku), name == null ? variant.getVariantName() : required(name), status, clock.now()); return variant; }
    private void replaceAttributes(CatalogItemEntity item, List<CatalogAggregateValidator.NormalizedAttribute> normalized) {
        attributes.deleteByCatalogItemId(item.getId()); attributes.flush();
        for (CatalogAggregateValidator.NormalizedAttribute value : normalized) attributes.save(CatalogItemAttributeValueEntity.create(ids.generate(), item.getId(), value.definition().getId(), value.request().valueText(), value.request().valueNumber(), value.request().valueBoolean(), value.request().optionId()));
    }
    private List<AttributeValueRequest> currentAttributes(CatalogItemEntity item) {
        Map<UUID, String> keyByDefinition = new HashMap<>();
        for (var definition : categoryAttributes.findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(item.getPrimaryCategoryId())) keyByDefinition.put(definition.getId(), definition.getAttributeKey());
        List<AttributeValueRequest> result = new ArrayList<>();
        for (CatalogItemAttributeValueEntity value : attributes.findAllByCatalogItemId(item.getId())) {
            String key = keyByDefinition.get(value.getCategoryAttributeId());
            if (key == null) throw reject(ErrorCode.CATALOG_ATTRIBUTE_INVALID, "Existing attribute is incompatible with the target category.");
            result.add(new AttributeValueRequest(key, value.getValueText(), value.getValueNumber(), value.getValueBoolean(), value.getAttributeOptionId()));
        }
        return result;
    }
    private void replaceTags(CatalogItemEntity item, List<String> requested, Instant now) {
        itemTags.deleteByIdCatalogItemId(item.getId()); itemTags.flush(); Set<String> names = new HashSet<>();
        for (String raw : requested) { String name = required(raw); if (name.length() > 100) throw reject(ErrorCode.VALIDATION_FAILED, "Tag must not exceed 100 characters."); String normalized = name.toLowerCase(Locale.ROOT); if (!names.add(normalized)) continue; TagEntity tag = tags.findByNormalizedName(normalized).orElseGet(() -> tags.save(TagEntity.create(ids.generate(), name, normalized, now))); itemTags.save(CatalogItemTagEntity.associate(item, tag)); }
    }
    private void replaceMedia(CatalogItemEntity item, List<UUID> requested, UUID actorId) {
        itemMedia.deleteByIdCatalogItemId(item.getId()); itemMedia.flush(); Set<UUID> ids = new HashSet<>(); int order = 0;
        for (UUID mediaId : requested) if (ids.add(mediaId)) { MediaAssetEntity asset = media.requireReadyOwned(mediaId, actorId, Set.of(MediaPurpose.CATALOG_IMAGE)); itemMedia.save(CatalogItemMediaEntity.image(item, asset, order, order++ == 0)); }
    }
    private Map<UUID, OptionValueEntity> valuesFor(List<OptionGroupEntity> groups) {
        if (groups.isEmpty()) return Map.of(); List<OptionValueEntity> values = optionValues.findAllByOptionGroupIdInOrderBySortOrderAscIdAsc(groups.stream().map(OptionGroupEntity::getId).toList()); Map<UUID, OptionValueEntity> byId = new HashMap<>(); for (OptionValueEntity value : values) byId.put(value.getId(), value); return byId;
    }
    private boolean hasCombination(UUID itemId, List<UUID> selectedIds) {
        Set<UUID> expected = Set.copyOf(selectedIds);
        for (VariantEntity existing : variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId)) {
            Set<UUID> current = new HashSet<>(); for (VariantOptionValueEntity association : variantOptions.findAllByIdVariantId(existing.getId())) current.add(association.getId().getOptionValueId());
            if (current.equals(expected)) return true;
        }
        return false;
    }
    private static void validateOffer(CatalogItemType type, MerchantLocationEntity location, InventoryMode inventoryMode) {
        if (type == CatalogItemType.PRODUCT && inventoryMode == null) throw offerReject("Product offer requires TRACKED or UNTRACKED inventory mode.");
        if (type == CatalogItemType.SERVICE && inventoryMode != null) throw offerReject("Service offer must not define inventory mode.");
        if (type == CatalogItemType.SERVICE && !location.isServiceEnabled()) throw offerReject("Merchant location is not enabled for services.");
    }
    private static String required(String value) { if (value == null || value.strip().isEmpty()) throw reject(ErrorCode.VALIDATION_FAILED, "Value must not be blank."); return value.strip(); }
    private static String nullable(String value) { return value == null || value.strip().isEmpty() ? null : value.strip(); }
    private static ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."); }
    private static ApiException variantReject(String message) { return reject(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID, message); }
    private static ApiException offerReject(String message) { return reject(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, message); }
    private static ApiException reject(ErrorCode code, String message) { return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message); }
    public record EditResult(UUID id, long version) {}
}
