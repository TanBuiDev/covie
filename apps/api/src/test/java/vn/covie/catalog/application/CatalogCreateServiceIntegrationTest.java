package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.api.dto.create.AttributeValueRequest;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.persistence.CategoryAttributeOptionRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;

@Transactional
class CatalogCreateServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired CatalogAggregateValidator validator;
    @Autowired CategoryRepository categories;
    @Autowired CategoryAttributeRepository attributes;
    @Autowired CategoryAttributeOptionRepository options;

    @Test
    void loadsCategorySchemaOnceAndNormalizesTypedAttributeValues() {
        UUID categoryId = category();
        attributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), categoryId, "Weight", "weight",
                CategoryAttributeType.NUMBER, true, true, 0));
        var normalized = validator.validate(categoryId, CatalogItemType.PRODUCT,
                List.of(new AttributeValueRequest("weight", null, new BigDecimal("1.25"), null, null)));
        assertThat(normalized.values()).hasSize(1);
        assertThat(normalized.values().getFirst().definition().getAttributeKey()).isEqualTo("weight");
    }

    @Test
    void rejectsSelectOptionThatBelongsToAnotherCategoryAttribute() {
        UUID categoryId = category();
        CategoryAttributeEntity color = attributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), categoryId,
                "Color", "color", CategoryAttributeType.SINGLE_SELECT, false, true, 0));
        CategoryAttributeEntity size = attributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), categoryId,
                "Size", "size", CategoryAttributeType.SINGLE_SELECT, false, true, 1));
        UUID sizeOption = options.saveAndFlush(CategoryAttributeOptionEntity.create(UUID.randomUUID(), size.getId(), "S", 0)).getId();
        assertThatThrownBy(() -> validator.validate(categoryId, CatalogItemType.PRODUCT,
                List.of(new AttributeValueRequest("color", null, null, null, sizeOption))))
                .isInstanceOf(ApiException.class).extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CATALOG_ATTRIBUTE_INVALID);
    }

    private UUID category() {
        return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Catalog", "catalog-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
    }
}
