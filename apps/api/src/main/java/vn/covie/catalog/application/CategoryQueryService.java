package vn.covie.catalog.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.persistence.CategoryAttributeOptionRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CategoryQueryService {
    private final CategoryRepository categories;
    private final CategoryAttributeRepository attributes;
    private final CategoryAttributeOptionRepository options;

    public CategoryQueryService(CategoryRepository categories, CategoryAttributeRepository attributes,
            CategoryAttributeOptionRepository options) {
        this.categories = categories;
        this.attributes = attributes;
        this.options = options;
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> list(UUID parentId, CatalogItemType type) {
        Collection<CategoryCatalogType> types = compatibleTypes(type);
        List<CategoryEntity> result = parentId == null
                ? categories.findAllByParentIdIsNullAndStatusAndCatalogTypeInAndDeletedAtIsNullOrderBySortOrderAscNameAscIdAsc(CategoryStatus.ACTIVE, types)
                : categories.findAllByParentIdAndStatusAndCatalogTypeInAndDeletedAtIsNullOrderBySortOrderAscNameAscIdAsc(parentId, CategoryStatus.ACTIVE, types);
        return result.stream().map(CategorySummary::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDetail detail(UUID categoryId) {
        CategoryEntity category = categories.findByIdAndStatusAndDeletedAtIsNull(categoryId, CategoryStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        List<CategoryAttributeEntity> definitions = attributes.findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(categoryId);
        Map<UUID, List<CategoryAttributeOptionEntity>> optionsByAttribute = definitions.isEmpty() ? Map.of()
                : options.findAllByCategoryAttributeIdInOrderBySortOrderAscOptionValueAscIdAsc(
                        definitions.stream().map(CategoryAttributeEntity::getId).toList())
                        .stream().collect(Collectors.groupingBy(CategoryAttributeOptionEntity::getCategoryAttributeId));
        return CategoryDetail.from(category, definitions, optionsByAttribute);
    }

    private static List<CategoryCatalogType> compatibleTypes(CatalogItemType type) {
        if (type == null) return List.of(CategoryCatalogType.PRODUCT, CategoryCatalogType.SERVICE, CategoryCatalogType.BOTH);
        return type == CatalogItemType.PRODUCT
                ? List.of(CategoryCatalogType.PRODUCT, CategoryCatalogType.BOTH)
                : List.of(CategoryCatalogType.SERVICE, CategoryCatalogType.BOTH);
    }

    public record CategorySummary(UUID id, UUID parentId, String name, String slug, CategoryCatalogType catalogType,
            int sortOrder) {
        static CategorySummary from(CategoryEntity category) {
            return new CategorySummary(category.getId(), category.getParentId(), category.getName(), category.getSlug(),
                    category.getCatalogType(), category.getSortOrder());
        }
    }

    public record CategoryDetail(UUID id, UUID parentId, String name, String slug, CategoryCatalogType catalogType,
            int sortOrder, List<AttributeDefinition> attributes) {
        static CategoryDetail from(CategoryEntity category, List<CategoryAttributeEntity> definitions,
                Map<UUID, List<CategoryAttributeOptionEntity>> optionsByAttribute) {
            return new CategoryDetail(category.getId(), category.getParentId(), category.getName(), category.getSlug(),
                    category.getCatalogType(), category.getSortOrder(), definitions.stream()
                            .map(attribute -> AttributeDefinition.from(attribute,
                                    optionsByAttribute.getOrDefault(attribute.getId(), List.of())))
                            .toList());
        }
    }

    public record AttributeDefinition(UUID id, String name, String attributeKey, CategoryAttributeType dataType,
            boolean required, boolean filterable, int sortOrder, List<AttributeOption> options) {
        static AttributeDefinition from(CategoryAttributeEntity attribute,
                List<CategoryAttributeOptionEntity> options) {
            return new AttributeDefinition(attribute.getId(), attribute.getName(), attribute.getAttributeKey(),
                    attribute.getDataType(), attribute.isRequired(), attribute.isFilterable(), attribute.getSortOrder(),
                    options.stream().map(AttributeOption::from).toList());
        }
    }

    public record AttributeOption(UUID id, String value, int sortOrder) {
        static AttributeOption from(CategoryAttributeOptionEntity option) {
            return new AttributeOption(option.getId(), option.getOptionValue(), option.getSortOrder());
        }
    }
}
