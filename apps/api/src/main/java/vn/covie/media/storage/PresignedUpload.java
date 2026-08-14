package vn.covie.media.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record PresignedUpload(URI url, String method, Instant expiresAt, Map<String, String> requiredHeaders) {}
