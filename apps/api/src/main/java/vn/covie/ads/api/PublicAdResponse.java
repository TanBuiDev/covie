package vn.covie.ads.api;
import java.time.Instant;
import java.util.UUID;
import vn.covie.ads.api.AdCampaignRequest.*;
public record PublicAdResponse(UUID id, String title, String body, String ctaLabel, String targetUrl,
    CreativeType creativeType, Template template, String mediaUrl, Placement placement, Instant endsAt,
    String deliveryToken, Instant deliveryExpiresAt) {}
