package vn.covie.social.api.dto;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record SocialActivityEntry(UUID itemId,String itemName,String merchantName,URI coverUrl,
        Instant activityAt,UUID commentId,String commentExcerpt,boolean itemAvailable) {}
