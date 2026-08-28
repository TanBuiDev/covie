package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;

class CatalogManagementQueryServiceTest {
    @Test
    void returnsVersionedAggregateAndLifecycleCapabilities() {
        UUID merchantId = UUID.randomUUID(), itemId = UUID.randomUUID(), categoryId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID(), locationId = UUID.randomUUID(), offerId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Máy lọc", "may-loc", "Mô tả", Instant.EPOCH);
        VariantEntity variant = VariantEntity.createDefault(variantId, itemId, "Mặc định", Instant.EPOCH);
        LocationVariantEntity offer = LocationVariantEntity.create(offerId, locationId, variantId,
                1_000_000, "VND", true, InventoryMode.TRACKED, 30, Instant.EPOCH);
        CatalogItemRepository items = mock(CatalogItemRepository.class);
        when(items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId)).thenReturn(Optional.of(item));
        VariantRepository variants = mock(VariantRepository.class);
        when(variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId)).thenReturn(List.of(variant));
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findAllActiveRecordsByCatalogItemId(itemId)).thenReturn(List.of(offer));
        CatalogManagementQueryService service = new CatalogManagementQueryService(
                mock(MerchantAccessPolicy.class), items, variants, offers,
                mock(VariantOptionValueRepository.class), mock(CatalogItemMediaRepository.class));

        var detail = service.get(merchantId, itemId);

        assertThat(detail.version()).isZero();
        assertThat(detail.variants()).singleElement().satisfies(value -> assertThat(value.version()).isZero());
        assertThat(detail.locationOffers()).singleElement().satisfies(value -> assertThat(value.version()).isZero());
        assertThat(detail.lifecycle().canPublish()).isTrue();
        assertThat(detail.lifecycle().canPause()).isFalse();
    }

    @Test
    void suppressesPublishWhileModerationIsPending() {
        UUID merchantId = UUID.randomUUID(), itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, UUID.randomUUID(), "Máy lọc", "may-loc", null, Instant.EPOCH);
        item.requireModeration(Instant.EPOCH);
        CatalogItemRepository items = mock(CatalogItemRepository.class);
        when(items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId)).thenReturn(Optional.of(item));
        VariantRepository variants = mock(VariantRepository.class);
        when(variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId)).thenReturn(List.of());
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findAllActiveRecordsByCatalogItemId(itemId)).thenReturn(List.of());
        CatalogItemMediaRepository media = mock(CatalogItemMediaRepository.class);
        when(media.findAllMediaAssetsByCatalogItemId(itemId)).thenReturn(List.of());
        CatalogManagementQueryService service = new CatalogManagementQueryService(
                mock(MerchantAccessPolicy.class), items, variants, offers,
                mock(VariantOptionValueRepository.class), media);

        assertThat(service.get(merchantId, itemId).lifecycle().canPublish()).isFalse();
    }
}
