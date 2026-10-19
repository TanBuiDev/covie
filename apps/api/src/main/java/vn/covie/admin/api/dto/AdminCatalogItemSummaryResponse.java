package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;

public record AdminCatalogItemSummaryResponse(UUID id, String name, CatalogItemType type,
        ListingKind listingKind, UUID merchantId, String merchantName, UUID categoryId,
        String categoryName, ListingStatus listingStatus, CatalogModerationStatus moderationStatus,
        Instant createdAt, Instant updatedAt, Instant deletedAt, PublicMediaResponse primaryMedia,
        @io.swagger.v3.oas.annotations.media.Schema(description="Whether a stored social caption is present (non-null), independent of the commercial description.") boolean hasCaption,
        @io.swagger.v3.oas.annotations.media.Schema(description="Whether the item has an associated, non-deleted CATALOG_VIDEO asset, independent of video playback activation.") boolean hasVideo) {
    public AdminCatalogItemSummaryResponse withPrimaryMedia(PublicMediaResponse media) {
        return new AdminCatalogItemSummaryResponse(id,name,type,listingKind,merchantId,merchantName,
                categoryId,categoryName,listingStatus,moderationStatus,createdAt,updatedAt,deletedAt,media,hasCaption,hasVideo);
    }
}
