package vn.covie.social.api.dto;

import java.util.UUID;

public record SocialItemSummary(UUID itemId, long likes, long comments, boolean liked, boolean saved) {}
