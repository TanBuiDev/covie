package vn.covie.catalog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.api.dto.create.AttributeValueRequest;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.persistence.CategoryAttributeOptionRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Loads the category schema once and returns values whose type and option ownership are already checked. */
@Service
public class CatalogAggregateValidator {
    private final CategoryRepository categories;
    private final CategoryAttributeRepository attributes;
    private final CategoryAttributeOptionRepository options;
    public CatalogAggregateValidator(CategoryRepository categories, CategoryAttributeRepository attributes,
            CategoryAttributeOptionRepository options) { this.categories = categories; this.attributes = attributes; this.options = options; }

    public NormalizedAttributes validate(UUID categoryId, CatalogItemType itemType, List<AttributeValueRequest> requested) {
        CategoryEntity category = categories.findByIdAndStatusAndDeletedAtIsNull(categoryId, vn.covie.catalog.model.CategoryStatus.ACTIVE)
                .orElseThrow(() -> reject(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Category was not found."));
        if (!category.getCatalogType().supports(itemType)) throw reject(ErrorCode.CATEGORY_NOT_COMPATIBLE,
                HttpStatus.UNPROCESSABLE_ENTITY, "Category does not support the catalog item type.");
        List<CategoryAttributeEntity> definitions = attributes.findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(categoryId);
        Map<String, CategoryAttributeEntity> byKey = new HashMap<>();
        for (CategoryAttributeEntity definition : definitions) byKey.put(definition.getAttributeKey(), definition);
        Map<UUID, CategoryAttributeOptionEntity> optionsById = new HashMap<>();
        if (!definitions.isEmpty()) for (CategoryAttributeOptionEntity option : options.findAllByCategoryAttributeIdInOrderBySortOrderAscOptionValueAscIdAsc(
                definitions.stream().map(CategoryAttributeEntity::getId).toList())) optionsById.put(option.getId(), option);
        List<AttributeValueRequest> values = requested == null ? List.of() : requested;
        Map<String, Integer> occurrences = new HashMap<>();
        List<NormalizedAttribute> normalized = new ArrayList<>();
        for (AttributeValueRequest value : values) {
            CategoryAttributeEntity definition = byKey.get(value.attributeKey());
            if (definition == null) throw reject(ErrorCode.CATALOG_ATTRIBUTE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Unknown category attribute.");
            int count = occurrences.merge(definition.getAttributeKey(), 1, Integer::sum);
            if (definition.getDataType() != CategoryAttributeType.MULTI_SELECT && count > 1)
                throw reject(ErrorCode.CATALOG_ATTRIBUTE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Attribute may be supplied once.");
            validateValue(definition, value, optionsById);
            normalized.add(new NormalizedAttribute(definition, value));
        }
        for (CategoryAttributeEntity definition : definitions) if (definition.isRequired() && !occurrences.containsKey(definition.getAttributeKey()))
            throw reject(ErrorCode.CATALOG_ATTRIBUTE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Required category attribute is missing.");
        return new NormalizedAttributes(category, normalized);
    }

    private static void validateValue(CategoryAttributeEntity definition, AttributeValueRequest value,
            Map<UUID, CategoryAttributeOptionEntity> optionsById) {
        boolean valid = switch (definition.getDataType()) {
            case TEXT -> value.valueText() != null && value.valueNumber() == null && value.valueBoolean() == null && value.optionId() == null;
            case NUMBER -> value.valueText() == null && value.valueNumber() != null && value.valueBoolean() == null && value.optionId() == null;
            case BOOLEAN -> value.valueText() == null && value.valueNumber() == null && value.valueBoolean() != null && value.optionId() == null;
            case SINGLE_SELECT, MULTI_SELECT -> value.valueText() == null && value.valueNumber() == null && value.valueBoolean() == null
                    && value.optionId() != null && optionsById.containsKey(value.optionId())
                    && optionsById.get(value.optionId()).getCategoryAttributeId().equals(definition.getId());
        };
        if (!valid) throw reject(ErrorCode.CATALOG_ATTRIBUTE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Attribute value is invalid for its definition.");
    }
    private static ApiException reject(ErrorCode code, HttpStatus status, String message) { return new ApiException(code, status, message); }
    public record NormalizedAttributes(CategoryEntity category, List<NormalizedAttribute> values) {}
    public record NormalizedAttribute(CategoryAttributeEntity definition, AttributeValueRequest request) {}
}
