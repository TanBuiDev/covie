package vn.covie.social.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SocialComment(UUID id, UUID itemId, String authorName, String body, Instant createdAt, boolean own) {}
