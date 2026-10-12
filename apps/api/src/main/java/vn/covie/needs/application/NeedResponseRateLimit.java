package vn.covie.needs.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.ratelimit.RateLimitGroup;
import vn.covie.ratelimit.RedisTokenBucketRateLimiter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Resolves rate-limit tenants from authorized records, never from client headers. */
@Component
public class NeedResponseRateLimit {
    private final RedisTokenBucketRateLimiter limiter;
    private final CurrentActorProvider actors;
    private final MerchantLocationRepository locations;
    private final NeedResponseRepository responses;
    private final MerchantAccessPolicy access;

    public NeedResponseRateLimit(RedisTokenBucketRateLimiter limiter, CurrentActorProvider actors,
            MerchantLocationRepository locations, NeedResponseRepository responses, MerchantAccessPolicy access) {
        this.limiter = limiter;
        this.actors = actors;
        this.locations = locations;
        this.responses = responses;
        this.access = access;
    }

    public void forLocation(UUID locationId) {
        if (!limiter.enabled()) return;
        UUID userId = actors.currentActor().userId();
        var location = locations.findById(locationId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(NeedResponseRateLimit::notFound);
        require(userId, location.getMerchantProfileId(), location.getId());
    }

    public void forResponse(UUID needId, UUID responseId) {
        if (!limiter.enabled()) return;
        UUID userId = actors.currentActor().userId();
        var response = responses.findById(responseId).filter(value -> value.getNeedRequestId().equals(needId))
                .orElseThrow(NeedResponseRateLimit::notFound);
        require(userId, response.getMerchantProfileId(), response.getMerchantLocationId());
    }

    private void require(UUID userId, UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.RESPOND_TO_NEEDS);
        limiter.require(RateLimitGroup.NEED_RESPONSE, userId + ":" + merchantId);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
