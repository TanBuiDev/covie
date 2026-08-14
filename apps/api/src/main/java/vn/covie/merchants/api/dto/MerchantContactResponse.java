package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.ContactStatus;
import vn.covie.merchants.model.ContactType;

public record MerchantContactResponse(
        UUID id,
        UUID merchantId,
        UUID merchantLocationId,
        ContactType contactType,
        String value,
        UUID qrMediaId,
        boolean primary,
        boolean publicContact,
        ContactStatus status,
        long version) {}
