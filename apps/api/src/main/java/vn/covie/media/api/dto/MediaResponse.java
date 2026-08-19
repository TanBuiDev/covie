package vn.covie.media.api.dto;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import vn.covie.media.model.MediaPurpose;

public record MediaResponse(UUID mediaId, MediaPurpose purpose, String contentType, long fileSize, URI url, Instant verifiedAt) {}
