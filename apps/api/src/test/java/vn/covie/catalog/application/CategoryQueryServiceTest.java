package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;

class CategoryQueryServiceTest {
    @Test
    void product_listing_requests_product_and_both_active_categories() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryAttributeRepository attributes = mock(CategoryAttributeRepository.class);
        CategoryAttributeOptionRepository options = mock(CategoryAttributeOptionRepository.class);
        UUID categoryId = UUID.randomUUID();
        when(categories.findAllByParentIdIsNullAndStatusAndCatalogTypeInAndDeletedAtIsNullOrderBySortOrderAscNameAscIdAsc(
                CategoryStatus.ACTIVE, List.of(CategoryCatalogType.PRODUCT, CategoryCatalogType.BOTH)))
                .thenReturn(List.of(CategoryEntity.create(categoryId, null, "Tea", "tea", CategoryCatalogType.BOTH, CategoryStatus.ACTIVE, 2)));

        List<CategoryQueryService.CategorySummary> result = new CategoryQueryService(categories, attributes, options)
                .list(null, CatalogItemType.PRODUCT);

        assertThat(result).extracting(CategoryQueryService.CategorySummary::id).containsExactly(categoryId);
        verifyNoInteractions(attributes, options);
    }

    @Test
    void detail_hydrates_ordered_attribute_options_for_the_active_category() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryAttributeRepository attributes = mock(CategoryAttributeRepository.class);
        CategoryAttributeOptionRepository options = mock(CategoryAttributeOptionRepository.class);
        UUID categoryId = UUID.randomUUID(); UUID attributeId = UUID.randomUUID(); UUID optionId = UUID.randomUUID();
        when(categories.findByIdAndStatusAndDeletedAtIsNull(categoryId, CategoryStatus.ACTIVE)).thenReturn(java.util.Optional.of(
                CategoryEntity.create(categoryId, null, "Tea", "tea", CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)));
        when(attributes.findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(categoryId)).thenReturn(List.of(
                CategoryAttributeEntity.create(attributeId, categoryId, "Roast", "roast", CategoryAttributeType.SINGLE_SELECT, true, true, 1)));
        when(options.findAllByCategoryAttributeIdInOrderBySortOrderAscOptionValueAscIdAsc(List.of(attributeId))).thenReturn(List.of(
                CategoryAttributeOptionEntity.create(optionId, attributeId, "Dark", 0)));

        CategoryQueryService.CategoryDetail result = new CategoryQueryService(categories, attributes, options).detail(categoryId);

        assertThat(result.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.attributeKey()).isEqualTo("roast");
            assertThat(attribute.options()).extracting(CategoryQueryService.AttributeOption::value).containsExactly("Dark");
        });
    }
}
