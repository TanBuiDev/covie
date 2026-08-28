package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;

public record AdminListingSummaryResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        ListingStatus listingStatus,
        CatalogModerationStatus moderationStatus,
        UUID moderationCaseId,
        Instant createdAt,
        Instant updatedAt) {}
