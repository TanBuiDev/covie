package vn.covie.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.application.CategoryQueryService;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.TagEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.support.PostgresIntegrationTest;

class CatalogPersistenceTest extends PostgresIntegrationTest {
    @Autowired private CategoryRepository categories;
    @Autowired private CatalogItemRepository items;
    @Autowired private VariantRepository variants;
    @Autowired private LocationVariantRepository locationVariants;
    @Autowired private TagRepository tags;
    @Autowired private CatalogItemTagRepository itemTags;
    @Autowired private CatalogItemMediaRepository itemMedia;
    @Autowired private OptionGroupRepository optionGroups;
    @Autowired private OptionValueRepository optionValues;
    @Autowired private VariantOptionValueRepository variantOptionValues;
    @Autowired private CategoryAttributeRepository categoryAttributes;
    @Autowired private CategoryAttributeOptionRepository categoryAttributeOptions;
    @Autowired private CategoryQueryService categoryQueries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MerchantProfileRepository merchants;
    @Autowired private MerchantLocationRepository locations;
    @Autowired private MediaAssetRepository mediaAssets;

    @Test
    @Transactional
    void associations_are_removed_without_deleting_shared_tag_media_or_location() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        MerchantProfileEntity merchant = merchants.save(MerchantProfileEntity.create(UUID.randomUUID(), "Catalog shop", "catalog-shop-" + UUID.randomUUID(), now));
        MerchantLocationEntity location = locations.save(MerchantLocationEntity.create(UUID.randomUUID(), merchant.getId(), "Main", "1 Main St", 10.0, 106.0, now));
        CategoryEntity category = categories.save(CategoryEntity.create(UUID.randomUUID(), null, "Drinks", "drinks-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0));
        CatalogItemEntity item = items.save(CatalogItemEntity.create(UUID.randomUUID(), merchant.getId(), CatalogItemType.PRODUCT, ListingKind.STANDARD, category.getId(), "Tea", "tea-" + UUID.randomUUID(), null, now));
        VariantEntity variant = variants.save(VariantEntity.create(UUID.randomUUID(), item.getId(), "TEA-500", "500 ml", false, 0, now));
        LocationVariantEntity offer = locationVariants.save(LocationVariantEntity.create(UUID.randomUUID(), location.getId(), variant.getId(), 15000, "VND", true, InventoryMode.TRACKED, null, now));
        TagEntity tag = tags.save(TagEntity.create(UUID.randomUUID(), "Fresh", "fresh", now));
        MediaAssetEntity asset = mediaAssets.save(MediaAssetEntity.pending(UUID.randomUUID(), null, "catalog/test.jpg", MediaPurpose.CATALOG_IMAGE, "image/jpeg", 100, now.plusSeconds(600), now));
        OptionGroupEntity group = optionGroups.save(OptionGroupEntity.create(UUID.randomUUID(), item.getId(), "Volume", 0));
        OptionValueEntity option = optionValues.save(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "500 ml", 0));

        itemTags.save(vn.covie.catalog.model.CatalogItemTagEntity.associate(item, tag));
        itemMedia.save(CatalogItemMediaEntity.image(item, asset, 0, true));
        variantOptionValues.save(VariantOptionValueEntity.associate(variant, option));
        itemTags.deleteByIdCatalogItemIdAndIdTagId(item.getId(), tag.getId());
        itemMedia.deleteByIdCatalogItemIdAndIdMediaAssetId(item.getId(), asset.getId());
        variantOptionValues.deleteByIdVariantIdAndIdOptionValueId(variant.getId(), option.getId());
        locationVariants.delete(offer);
        itemTags.flush();

        assertThat(itemTags.existsByIdCatalogItemIdAndIdTagId(item.getId(), tag.getId())).isFalse();
        assertThat(itemMedia.existsByIdCatalogItemIdAndIdMediaAssetId(item.getId(), asset.getId())).isFalse();
        assertThat(variantOptionValues.existsByIdVariantIdAndIdOptionValueId(variant.getId(), option.getId())).isFalse();
        assertThat(tags.existsById(tag.getId())).isTrue();
        assertThat(mediaAssets.existsById(asset.getId())).isTrue();
        assertThat(optionValues.existsById(option.getId())).isTrue();
        assertThat(locations.existsById(location.getId())).isTrue();
    }

    @Test
    @Transactional
    void usable_offer_excludes_soft_deleted_variant_and_catalog_item() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        MerchantProfileEntity merchant = merchants.save(MerchantProfileEntity.create(UUID.randomUUID(), "Usable shop", "usable-shop-" + UUID.randomUUID(), now));
        MerchantLocationEntity location = locations.save(MerchantLocationEntity.create(UUID.randomUUID(), merchant.getId(), "Main", "1 Main St", 10.0, 106.0, now));
        CategoryEntity category = categories.save(CategoryEntity.create(UUID.randomUUID(), null, "Services", "services-" + UUID.randomUUID(), CategoryCatalogType.SERVICE, CategoryStatus.ACTIVE, 0));
        CatalogItemEntity item = items.save(CatalogItemEntity.create(UUID.randomUUID(), merchant.getId(), CatalogItemType.SERVICE, ListingKind.STANDARD, category.getId(), "Wash", "wash-" + UUID.randomUUID(), null, now));
        VariantEntity variant = variants.save(VariantEntity.createDefault(UUID.randomUUID(), item.getId(), "Default", now));
        LocationVariantEntity offer = locationVariants.save(LocationVariantEntity.create(UUID.randomUUID(), location.getId(), variant.getId(), 50000, "VND", true, null, 30, now));

        assertThat(locationVariants.findUsableById(offer.getId())).isPresent();
        variant.delete(now.plusSeconds(1));
        variants.flush();

        assertThat(locationVariants.findUsableById(offer.getId())).isEmpty();
        assertThat(locationVariants.findByIdAndDeletedAtIsNull(offer.getId())).isPresent();
    }

    @Test
    @Transactional
    void category_queries_filter_active_types_and_hydrate_detail_metadata() {
        UUID productId = UUID.randomUUID();
        UUID bothId = UUID.randomUUID();
        categories.save(CategoryEntity.create(productId, null, "Zebra", "zebra-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 2));
        categories.save(CategoryEntity.create(bothId, null, "Alpha", "alpha-" + UUID.randomUUID(), CategoryCatalogType.BOTH, CategoryStatus.ACTIVE, 1));
        categories.save(CategoryEntity.create(UUID.randomUUID(), null, "Service", "service-" + UUID.randomUUID(), CategoryCatalogType.SERVICE, CategoryStatus.ACTIVE, 0));
        categories.save(CategoryEntity.create(UUID.randomUUID(), null, "Hidden", "hidden-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.INACTIVE, 0));
        UUID softDeletedId = UUID.randomUUID();
        categories.save(CategoryEntity.create(softDeletedId, null, "Deleted", "deleted-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 3));
        UUID softDeletedChildId = UUID.randomUUID();
        categories.save(CategoryEntity.create(softDeletedChildId, productId, "Deleted child", "deleted-child-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0));
        categories.flush();
        jdbc.update("update categories set deleted_at = ? where id = ?", java.sql.Timestamp.from(now()), softDeletedId);
        jdbc.update("update categories set deleted_at = ? where id = ?", java.sql.Timestamp.from(now()), softDeletedChildId);
        UUID attributeId = UUID.randomUUID();
        categoryAttributes.save(CategoryAttributeEntity.create(attributeId, productId, "Roast", "roast", CategoryAttributeType.SINGLE_SELECT, true, true, 0));
        categoryAttributeOptions.save(CategoryAttributeOptionEntity.create(UUID.randomUUID(), attributeId, "Dark", 0));

        assertThat(categoryQueries.list(null, CatalogItemType.PRODUCT))
                .extracting(CategoryQueryService.CategorySummary::id)
                .containsExactly(bothId, productId);
        assertThat(categoryQueries.list(productId, CatalogItemType.PRODUCT)).isEmpty();
        assertThat(categoryQueries.detail(productId).attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.attributeKey()).isEqualTo("roast");
            assertThat(attribute.options()).extracting(CategoryQueryService.AttributeOption::value).containsExactly("Dark");
        });
    }

    private static Instant now() {
        return Instant.parse("2026-08-14T00:00:00Z");
    }
}
