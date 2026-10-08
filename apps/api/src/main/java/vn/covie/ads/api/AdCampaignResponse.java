package vn.covie.ads.api;
import java.time.Instant;
import java.util.*;
import vn.covie.ads.api.AdCampaignRequest.*;
public record AdCampaignResponse(UUID id, UUID merchantId, String title, String body, String ctaLabel,
    String targetUrl, CreativeType creativeType, Template template, UUID mediaId, String mediaUrl,
    Placement placement, Device device, Instant startsAt, Instant endsAt, boolean enabled, int priority,
    Status status, String reviewReason, long version, List<String> allowedActions) {
    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }
}
