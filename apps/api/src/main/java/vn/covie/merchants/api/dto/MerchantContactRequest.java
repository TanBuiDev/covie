package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.covie.merchants.model.ContactType;

public record MerchantContactRequest(
        UUID merchantLocationId,
        @NotNull ContactType contactType,
        @Size(max = 1000) String value,
        UUID qrMediaId,
        boolean primary,
        boolean publicContact) {}
