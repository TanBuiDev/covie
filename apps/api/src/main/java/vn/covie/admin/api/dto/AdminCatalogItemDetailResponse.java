package vn.covie.admin.api.dto;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import vn.covie.moderation.api.dto.ModerationCaseSummaryResponse;

public record AdminCatalogItemDetailResponse(AdminCatalogItemSummaryResponse item, String description,
        List<Media> media, List<Location> locations, List<ModerationCaseSummaryResponse> moderationCases,
        long moderationCaseTotal, String moderationCaseNextCursor, String socialCaption) {
    public record Media(UUID id, String contentType, String status, int sortOrder, boolean cover, URI url,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            vn.covie.media.api.dto.CatalogVideoMetadata video) {}
    public record Location(UUID id, String name, String status) {}
}
