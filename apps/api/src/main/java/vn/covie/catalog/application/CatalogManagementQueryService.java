package vn.covie.catalog.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.api.dto.CatalogItemManagementDetailResponse;
import vn.covie.catalog.api.dto.CatalogItemManagementSummaryResponse;
import vn.covie.catalog.model.CatalogItemAttributeValueEntity;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemAttributeValueRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemTagRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaStatus;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogManagementQueryService {
    private final MerchantAccessPolicy access;
    private final CatalogItemRepository items;
    private final CategoryAttributeRepository categoryAttributes;
    private final CatalogItemAttributeValueRepository attributeValues;
    private final CatalogItemTagRepository tags;
    private final CatalogItemMediaRepository media;
    private final OptionGroupRepository optionGroups;
    private final OptionValueRepository optionValues;
    private final VariantRepository variants;
    private final VariantOptionValueRepository variantOptions;
    private final LocationVariantRepository offers;
    private final MerchantLocationRepository locations;
    private final MediaUrlResolver mediaUrls;
    private final CatalogManagementActionPolicy actions;

    public CatalogManagementQueryService(MerchantAccessPolicy access, CatalogItemRepository items,
            CategoryAttributeRepository categoryAttributes,
            CatalogItemAttributeValueRepository attributeValues,
            CatalogItemTagRepository tags,
            CatalogItemMediaRepository media,
            OptionGroupRepository optionGroups,
            OptionValueRepository optionValues,
            VariantRepository variants,
            VariantOptionValueRepository variantOptions,
            LocationVariantRepository offers,
            MerchantLocationRepository locations,
            MediaUrlResolver mediaUrls,
            CatalogManagementActionPolicy actions) {
        this.access = access;
        this.items = items;
        this.categoryAttributes = categoryAttributes;
        this.attributeValues = attributeValues;
        this.tags = tags;
        this.media = media;
        this.optionGroups = optionGroups;
        this.optionValues = optionValues;
        this.variants = variants;
        this.variantOptions = variantOptions;
        this.offers = offers;
        this.locations = locations;
        this.mediaUrls = mediaUrls;
        this.actions = actions;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemManagementSummaryResponse> list(UUID merchantId) {
        access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        return items.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchantId).stream()
                .map(CatalogManagementQueryService::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogItemManagementDetailResponse get(UUID merchantId, UUID itemId) {
        MerchantAccess merchantAccess = access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        CatalogItemEntity item = items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId)
                .orElseThrow(CatalogManagementQueryService::notFound);

        List<CategoryAttributeEntity> orderedDefinitions = categoryAttributes
                .findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(item.getPrimaryCategoryId());
        Map<UUID, CategoryAttributeEntity> attributeDefinitions = orderedDefinitions.stream()
                .collect(Collectors.toMap(CategoryAttributeEntity::getId, Function.identity()));
        List<CatalogItemManagementDetailResponse.CatalogManagementAttributeResponse> attributes = attributeValues
                .findAllByCatalogItemId(itemId).stream()
                .filter(value -> attributeDefinitions.containsKey(value.getCategoryAttributeId()))
                .sorted(attributeComparator(orderedDefinitions))
                .map(value -> attribute(value, attributeDefinitions.get(value.getCategoryAttributeId())))
                .toList();

        List<vn.covie.catalog.model.OptionGroupEntity> groups = optionGroups
                .findAllByCatalogItemIdOrderBySortOrderAscIdAsc(itemId);
        Map<UUID, List<OptionValueEntity>> valuesByGroup = optionValues
                .findAllByOptionGroupIdInOrderBySortOrderAscIdAsc(groups.stream().map(g -> g.getId()).toList()).stream()
                .collect(Collectors.groupingBy(OptionValueEntity::getOptionGroupId));
        List<CatalogItemManagementDetailResponse.CatalogManagementOptionGroupResponse> optionGroupResponses = groups.stream()
                .map(group -> new CatalogItemManagementDetailResponse.CatalogManagementOptionGroupResponse(group.getId(), group.getName(),
                        group.getSortOrder(), valuesByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(value -> new CatalogItemManagementDetailResponse.CatalogManagementOptionValueResponse(value.getId(),
                                        value.getValue(), value.getSortOrder()))
                                .toList()))
                .toList();
        Map<UUID, Integer> optionGroupOrder = new HashMap<>();
        for (int index = 0; index < groups.size(); index++) {
            for (OptionValueEntity value : valuesByGroup.getOrDefault(groups.get(index).getId(), List.of())) {
                optionGroupOrder.put(value.getId(), index);
            }
        }

        List<VariantEntity> itemVariants = variants
                .findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId);
        List<CatalogItemManagementDetailResponse.CatalogManagementVariantResponse> variantResponses = itemVariants.stream()
                .map(variant -> new CatalogItemManagementDetailResponse.CatalogManagementVariantResponse(variant.getId(), variant.getSku(),
                        variant.getVariantName(), variant.isDefaultVariant(), variant.getVariantStatus(),
                        variant.getSortOrder(), variantOptions.findAllByIdVariantId(variant.getId()).stream()
                                .map(association -> association.getId().getOptionValueId())
                                .sorted(Comparator.<UUID>comparingInt(id -> optionGroupOrder.getOrDefault(id, Integer.MAX_VALUE))
                                        .thenComparing(Function.identity()))
                                .toList(),
                        variant.getVersion(), actions.variantActions(merchantAccess, item)))
                .toList();

        List<vn.covie.catalog.model.LocationVariantEntity> itemOffers = offers
                .findAllActiveRecordsByCatalogItemId(itemId);
        Set<UUID> offerLocationIds = itemOffers.stream().map(offer -> offer.getMerchantLocationId())
                .collect(Collectors.toSet());
        Map<UUID, MerchantLocationEntity> offerLocations = locations.findAllById(offerLocationIds).stream()
                .filter(location -> merchantId.equals(location.getMerchantProfileId()))
                .collect(Collectors.toMap(MerchantLocationEntity::getId, Function.identity()));
        Set<UUID> visibleLocationIds = visibleLocationIds(merchantAccess, merchantId, offerLocations.keySet());
        List<CatalogItemManagementDetailResponse.CatalogManagementLocationOfferResponse> offerResponses = itemOffers.stream()
                .filter(offer -> visibleLocationIds.contains(offer.getMerchantLocationId()))
                .sorted(Comparator.comparing((vn.covie.catalog.model.LocationVariantEntity offer) ->
                                offerLocations.get(offer.getMerchantLocationId()).getName())
                        .thenComparing(offer -> offer.getId()))
                .map(offer -> new CatalogItemManagementDetailResponse.CatalogManagementLocationOfferResponse(offer.getId(),
                        offer.getMerchantLocationId(), offerLocations.get(offer.getMerchantLocationId()).getName(),
                        offer.getVariantId(), offer.getPrice(), offer.getCurrencyCode(), offer.isAvailable(),
                        offer.getInventoryMode(), offer.getPreparationTimeMinutes(), offer.getVersion(),
                        actions.offerActions(merchantAccess, offerLocations.get(offer.getMerchantLocationId()))))
                .toList();

        return new CatalogItemManagementDetailResponse(item.getId(), item.getItemType(), item.getListingKind(),
                item.getPrimaryCategoryId(), item.getName(), item.getDescription(), item.getListingStatus(),
                item.getModerationStatus(), item.getVersion(), actions.itemActions(merchantAccess, item), attributes,
                tags.findTagNamesByCatalogItemId(itemId),
                media.findAllAssociationsByCatalogItemId(itemId).stream()
                        .map(association -> new CatalogItemManagementDetailResponse.CatalogManagementMediaResponse(
                                association.getMediaAsset().getId(), association.getMediaAsset().getMimeType(),
                                association.getMediaAsset().getMediaStatus(),
                                association.getMediaAsset().getMediaStatus() == MediaStatus.READY
                                        ? mediaUrls.publicUrl(association.getMediaAsset()) : null,
                                association.getSortOrder(), association.isPrimary()))
                        .toList(),
                optionGroupResponses, variantResponses, offerResponses);
    }

    private Set<UUID> visibleLocationIds(MerchantAccess merchantAccess, UUID merchantId,
            Set<UUID> merchantOfferLocationIds) {
        if (merchantAccess.role() != MerchantRole.STAFF) return merchantOfferLocationIds;
        Set<UUID> assignedActive = locations.findAssignedActive(merchantId, merchantAccess.membershipId()).stream()
                .filter(location -> location.getLocationStatus() == MerchantLocationStatus.ACTIVE)
                .map(MerchantLocationEntity::getId)
                .collect(Collectors.toCollection(HashSet::new));
        assignedActive.retainAll(merchantOfferLocationIds);
        return assignedActive;
    }

    private static Comparator<CatalogItemAttributeValueEntity> attributeComparator(
            List<CategoryAttributeEntity> definitions) {
        Map<UUID, Integer> positions = new HashMap<>();
        int index = 0;
        for (CategoryAttributeEntity definition : definitions) positions.put(definition.getId(), index++);
        return Comparator.comparingInt(value -> positions.getOrDefault(value.getCategoryAttributeId(), Integer.MAX_VALUE));
    }

    private static CatalogItemManagementDetailResponse.CatalogManagementAttributeResponse attribute(
            CatalogItemAttributeValueEntity value, CategoryAttributeEntity definition) {
        return new CatalogItemManagementDetailResponse.CatalogManagementAttributeResponse(value.getCategoryAttributeId(),
                definition.getAttributeKey(), value.getValueText(), value.getValueNumber(), value.getValueBoolean(),
                value.getAttributeOptionId());
    }

    private static CatalogItemManagementSummaryResponse summary(CatalogItemEntity item) {
        return new CatalogItemManagementSummaryResponse(item.getId(), item.getItemType(), item.getListingKind(),
                item.getName(), item.getListingStatus(), item.getVersion());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
