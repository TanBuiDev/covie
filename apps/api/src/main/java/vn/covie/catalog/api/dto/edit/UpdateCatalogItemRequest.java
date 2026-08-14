package vn.covie.catalog.api.dto.edit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.api.dto.create.AttributeValueRequest;

/** Fields are optional so PATCH changes only explicitly supplied item members. */
public record UpdateCatalogItemRequest(@Size(max = 220) String name, @Size(max = 10000) String description,
        UUID primaryCategoryId, List<@Valid AttributeValueRequest> attributes, List<String> tags, List<UUID> mediaIds) {}
