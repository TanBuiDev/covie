package vn.covie.needs.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.moderation.model.ContentModerationStatus;
import vn.covie.needs.api.dto.NeedEligibleItemCursorPage;
import vn.covie.needs.api.dto.NeedEligibleItemResponse;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.persistence.NeedEligibleCandidateKey;
import vn.covie.needs.persistence.NeedEligibleCandidateRepository;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedEligibleItemReadService {
    private final MerchantAccessPolicy access;
    private final MerchantLocationRepository locations;
    private final NeedRequestRepository needs;
    private final NeedMatchingService matching;
    private final NeedPolicyProvider policies;
    private final AppClock clock;
    private final NeedEligibleCandidateRepository candidates;
    private final NeedEligibleItemCursorCodec cursors;
    private final NeedResponseEligibilityPolicy eligibility;

    public NeedEligibleItemReadService(MerchantAccessPolicy access, MerchantLocationRepository locations,
            NeedRequestRepository needs, NeedMatchingService matching, NeedPolicyProvider policies, AppClock clock,
            NeedEligibleCandidateRepository candidates, NeedEligibleItemCursorCodec cursors,
            NeedResponseEligibilityPolicy eligibility) {
        this.access = access;
        this.locations = locations;
        this.needs = needs;
        this.matching = matching;
        this.policies = policies;
        this.clock = clock;
        this.candidates = candidates;
        this.cursors = cursors;
        this.eligibility = eligibility;
    }

    public NeedEligibleItemCursorPage list(UUID merchantId, UUID needId, UUID locationId,
            Integer limit, String cursor) {
        access.requireLocation(merchantId, locationId, MerchantCapability.RESPOND_TO_NEEDS);
        var location = locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(NeedEligibleItemReadService::notFound);
        if (location.getLocationStatus() != MerchantLocationStatus.ACTIVE) throw notFound();
        NeedRequestEntity need = needs.findById(needId).orElseThrow(NeedEligibleItemReadService::notFound);
        if (!need.isActiveAt(clock.now()) || !visibleModeration(need)
                || !matching.canViewNeedAtLocation(merchantId, needId, locationId)) throw notFound();
        int max = policies.resolve(merchantId, need.getCategoryId(), need.getLocationAreaId()).maxResponseItems();
        int pageSize = Math.min(Math.max(limit == null ? 20 : limit, 1), 50);
        int batchSize = Math.min(Math.max(pageSize * 3, 30), 150);
        int scanCeiling = 500;
        int scanned = 0;
        NeedEligibleCandidateKey after = cursors.decode(cursor, merchantId, needId, locationId);
        List<NeedEligibleItemResponse> accepted = new ArrayList<>();
        while (accepted.size() < pageSize && scanned < scanCeiling) {
            int fetchSize = Math.min(batchSize, scanCeiling - scanned);
            var rows = candidates.findCandidates(merchantId, locationId, need.getNeedType(), after, fetchSize);
            if (rows.isEmpty()) break;
            for (var row : rows) {
                after = row.key();
                scanned++;
                var eligible = eligibility.evaluateForRead(need, merchantId, locationId,
                        row.locationVariantId(), clock.now());
                if (eligible.isPresent()) {
                    var source = eligible.get();
                    accepted.add(new NeedEligibleItemResponse(source.locationVariantId(), source.catalogItemId(),
                            source.variantId(), row.catalogItemName(), row.variantName(),
                            new NeedEligibleItemResponse.MoneyResponse(source.currentPrice(), source.currency()), true));
                }
                if (accepted.size() == pageSize || scanned == scanCeiling) break;
            }
            if (rows.size() < fetchSize) break;
        }
        boolean hasMore = after != null && !candidates.findCandidates(
                merchantId, locationId, need.getNeedType(), after, 1).isEmpty();
        String nextCursor = hasMore ? cursors.encode(merchantId, needId, locationId, after) : null;
        return new NeedEligibleItemCursorPage(List.copyOf(accepted), hasMore, nextCursor, max);
    }

    private boolean visibleModeration(NeedRequestEntity need) {
        return need.getModerationStatus() == ContentModerationStatus.NOT_REQUIRED
                || need.getModerationStatus() == ContentModerationStatus.APPROVED;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
