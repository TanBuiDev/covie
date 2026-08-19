package vn.covie.catalog.api.dto.publicview;

import java.util.UUID;
import vn.covie.shared.money.Money;

public record PublicOfferResponse(UUID locationVariantId, boolean available, Money price, Integer preparationTimeMinutes) {}
