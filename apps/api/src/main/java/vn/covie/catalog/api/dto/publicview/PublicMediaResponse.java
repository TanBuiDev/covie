package vn.covie.catalog.api.dto.publicview;

import java.net.URI;
import java.util.UUID;

public record PublicMediaResponse(UUID id, String contentType, URI url) {}
