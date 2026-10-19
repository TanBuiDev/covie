package vn.covie.catalog.api.dto.publicview;

import java.net.URI;
import java.util.UUID;

public record PublicMediaResponse(UUID id, String contentType, URI url,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        vn.covie.media.api.dto.CatalogVideoMetadata video) {
    public PublicMediaResponse(UUID id,String contentType,URI url) { this(id,contentType,url,null); }
}
