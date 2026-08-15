package vn.covie.catalog.api.dto.publicview;

import java.util.UUID;
import vn.covie.merchants.model.ContactType;

public record PublicMerchantContactResponse(UUID id, UUID merchantLocationId, ContactType type, String value, boolean primary) {}
