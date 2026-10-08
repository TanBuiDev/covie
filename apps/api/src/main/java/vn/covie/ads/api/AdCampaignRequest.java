package vn.covie.ads.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record AdCampaignRequest(
    @NotBlank @Size(max=160) String title, @Size(max=500) String body,
    @NotBlank @Size(max=60) String ctaLabel, @NotBlank @Size(max=2048) String targetUrl,
    @NotNull CreativeType creativeType, Template template, UUID mediaId,
    @NotNull Placement placement, @NotNull Device device,
    @NotNull Instant startsAt, @NotNull Instant endsAt, boolean enabled,
    @Min(0) @Max(1000) int priority) {
    public enum CreativeType { IMAGE, VIDEO, CSS }
    public enum Template { FLASH, NEARBY, SPOTLIGHT, HERO }
    public enum Placement { SIDEBAR_LEFT, HOME_HERO, IN_FEED, ITEM_DETAIL }
    public enum Device { DESKTOP, MOBILE, ALL }
}
