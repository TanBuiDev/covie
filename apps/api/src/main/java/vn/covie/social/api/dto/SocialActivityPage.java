package vn.covie.social.api.dto;

import java.util.List;

public record SocialActivityPage(List<SocialActivityEntry> items,String nextCursor) {}
