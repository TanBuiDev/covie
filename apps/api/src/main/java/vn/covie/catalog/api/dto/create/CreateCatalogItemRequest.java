package vn.covie.catalog.api.dto.create;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public record CreateCatalogItemRequest(@NotNull CatalogItemType type, @NotNull ListingKind listingKind,
        @NotNull UUID categoryId, @NotBlank @Size(max = 220) String name, @Size(max = 10000) String description,
        List<@Valid AttributeValueRequest> attributes, List<String> tags, List<UUID> mediaIds,
        List<@Valid OptionGroupCreateRequest> options, List<@Valid VariantCreateRequest> variants,
        @NotEmpty List<@Valid LocationOfferCreateRequest> locationOffers) {}
