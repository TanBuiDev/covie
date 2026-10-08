package vn.covie.needs.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.delivery.model.CoverageType;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.needs.api.dto.MerchantNeedCursorPage;
import vn.covie.needs.api.dto.MerchantNeedMatchResponse;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedDiscoveryRepository;
import vn.covie.needs.persistence.NeedMatchRow;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedMatchingService {
    private final MerchantAccessPolicy access; private final NeedDiscoveryRepository discovery; private final NeedRequestRepository needs;
    private final MerchantLocationRepository locations; private final NeedLocationContextFactory contexts;
    private final DeliveryCoverageEvaluator coverage; private final NeedPolicyProvider policies; private final AppClock clock;
    private final NeedDiscoveryCursorCodec cursors;
    public NeedMatchingService(MerchantAccessPolicy access, NeedDiscoveryRepository discovery, NeedRequestRepository needs,
            MerchantLocationRepository locations, NeedLocationContextFactory contexts, DeliveryCoverageEvaluator coverage,
            NeedPolicyProvider policies, AppClock clock, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.access=access;this.discovery=discovery;this.needs=needs;this.locations=locations;this.contexts=contexts;this.coverage=coverage;this.policies=policies;this.clock=clock;this.cursors=new NeedDiscoveryCursorCodec(mapper);
    }
    public MerchantNeedCursorPage discover(UUID merchantId, UUID requestedLocationId, Integer requestedLimit, String cursor) {
        Set<UUID> allowed = access.allowedLocationIds(merchantId, MerchantCapability.VIEW_NEEDS);
        if (requestedLocationId != null && !allowed.contains(requestedLocationId)) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
        int limit=Math.min(Math.max(requestedLimit==null?20:requestedLimit,1),50); int batch=Math.min(Math.max(limit*3,30),150);
        NeedDiscoveryCursor after=cursors.decode(cursor); List<MerchantNeedMatchResponse> out=new ArrayList<>(); boolean hasMore=false;
        for(int rounds=0;rounds<5 && out.size()<limit;rounds++) {
            List<NeedMatchRow> rows=discovery.findCandidates(merchantId,allowed,requestedLocationId,clock.now(),after,batch);
            if(rows.isEmpty()) break;
            for(NeedMatchRow row:rows) { after=new NeedDiscoveryCursor(row.textRank(),row.distanceMeters(),row.needId(),row.merchantLocationId()); if(eligible(row)) { out.add(map(merchantId,row)); if(out.size()==limit) break; } }
            hasMore=rows.size()==batch;
            if(rows.size()<batch || out.size()==limit) break;
        }
        return new MerchantNeedCursorPage(List.copyOf(out),hasMore,hasMore&&after!=null?cursors.encode(after):null);
    }
    /** Visibility-only variant used by reporting; it never exposes buyer location or response data. */
    public boolean canViewNeed(UUID merchantId, UUID needId) {
        Set<UUID> allowed = access.allowedLocationIds(merchantId, MerchantCapability.VIEW_NEEDS);
        NeedDiscoveryCursor after = null;
        for (int rounds = 0; rounds < 100; rounds++) {
            List<NeedMatchRow> rows = discovery.findCandidates(merchantId, allowed, null, clock.now(), after, 150);
            if (rows.isEmpty()) return false;
            for (NeedMatchRow row : rows) {
                after = new NeedDiscoveryCursor(row.textRank(), row.distanceMeters(), row.needId(), row.merchantLocationId());
                if (row.needId().equals(needId) && eligible(row)) return true;
            }
            if (rows.size() < 150) return false;
        }
        return false;
    }
    /** Direct-ID visibility must be scoped to the requested location's current discovery predicate. */
    public boolean canViewNeedAtLocation(UUID merchantId, UUID needId, UUID locationId) {
        Set<UUID> allowed = access.allowedLocationIds(merchantId, MerchantCapability.VIEW_NEEDS);
        if (!allowed.contains(locationId)) return false;
        return discovery.findCandidateForNeed(merchantId, locationId, needId, clock.now())
                .stream().anyMatch(this::eligible);
    }
    private boolean eligible(NeedMatchRow row) {
        if(row.type()==NeedType.PRODUCT && row.pickupEnabled()) return true;
        var need=needs.findById(row.needId()).orElse(null); if(need==null) return false;
        return coverage.evaluate(row.merchantLocationId(),row.type()==NeedType.PRODUCT?CoverageType.PRODUCT_DELIVERY:CoverageType.SERVICE_AREA,contexts.from(need)).eligible();
    }
    private MerchantNeedMatchResponse map(UUID merchantId, NeedMatchRow row) {
        int max=policies.resolve(merchantId,row.categoryId(),null).maxResponseItems();
        return new MerchantNeedMatchResponse(new MerchantNeedMatchResponse.Need(row.needId(),row.type(),row.title(),row.description(),row.categoryId(),row.expiresAt()),
                new MerchantNeedMatchResponse.ResolvedLocation(row.merchantLocationId(),row.merchantLocationName()),
                new MerchantNeedMatchResponse.BuyerArea(row.buyerAreaLabel()),row.distanceMeters(),row.alreadyResponded(),new MerchantNeedMatchResponse.ResponseConstraints(max));
    }
}
