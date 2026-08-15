package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import java.util.UUID;

public record PublicVariantResponse(
        UUID id,
        String name,
        boolean defaultVariant,
        List<UUID> optionValueIds,
        PublicOfferResponse offer) {}
