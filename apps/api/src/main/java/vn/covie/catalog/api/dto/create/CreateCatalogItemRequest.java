package vn.covie.catalog.api.dto.create;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public record CreateCatalogItemRequest(@NotNull CatalogItemType type, @NotNull ListingKind listingKind,
        @NotNull UUID categoryId, @NotBlank @Size(max = 220) String name, @Size(max = 10000) String description,
        List<@Valid AttributeValueRequest> attributes, List<String> tags, List<UUID> mediaIds,
        List<@Valid OptionGroupCreateRequest> options, List<@Valid VariantCreateRequest> variants,
        @NotEmpty List<@Valid LocationOfferCreateRequest> locationOffers,
        @Size(max=3000) @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(nullable=true,description="Optional plaintext social caption, maximum 3000 UTF-16 units.") String socialCaption) {}
