package vn.covie.social.api.dto;

import java.util.List;
import java.util.UUID;

public record SocialCommentPage(List<SocialComment> items, UUID nextCursor) {}
