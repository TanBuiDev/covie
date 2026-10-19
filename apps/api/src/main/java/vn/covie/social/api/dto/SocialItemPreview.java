package vn.covie.social.api.dto;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record SocialItemPreview(UUID itemId, String socialCaption, List<Media> media) {
    @io.swagger.v3.oas.annotations.media.Schema(name="SocialPreviewMedia")
    public record Media(UUID id, String mimeType, URI url, int sortOrder, boolean isCover,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            vn.covie.media.api.dto.CatalogVideoMetadata video) {}
}
