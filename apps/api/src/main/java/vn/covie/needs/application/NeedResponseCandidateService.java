package vn.covie.needs.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.needs.api.dto.NeedResponseCandidateCursorPage;
import vn.covie.needs.api.dto.NeedResponseCandidateResponse;
import vn.covie.needs.api.dto.NeedResponseItemResponse;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedResponseCandidateService {
    private static final int MAX_PAGE_SIZE = 50;

    private final NeedRequestRepository needs;
    private final LocationVariantRepository offers;
    private final NeedResponseEligibilityPolicy eligibility;
    private final AppClock clock;

    public NeedResponseCandidateService(
            NeedRequestRepository needs,
            LocationVariantRepository offers,
            NeedResponseEligibilityPolicy eligibility,
            AppClock clock) {
        this.needs = needs;
        this.offers = offers;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NeedResponseCandidateCursorPage list(
            UUID needId, UUID merchantId, UUID merchantLocationId, int requestedLimit, String cursor) {
        var need = needs.findById(needId).orElseThrow(NeedResponseCandidateService::notFound);
        int maxItems = eligibility.validateCandidateScope(need, merchantId, merchantLocationId, clock.now());
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        UUID after = decodeCursor(cursor);

        List<LocationVariantEntity> ordered = offers
                .findAllByMerchantLocationIdAndDeletedAtIsNull(merchantLocationId).stream()
                .sorted(Comparator.comparing(LocationVariantEntity::getId))
                .toList();
        List<NeedResponseCandidateResponse> accepted = new ArrayList<>(limit + 1);
        for (LocationVariantEntity offer : ordered) {
            if (after != null && offer.getId().compareTo(after) <= 0) continue;
            try {
                accepted.add(toResponse(eligibility.validateCandidateItem(
                        need, merchantId, merchantLocationId, offer.getId())));
            } catch (ApiException ignored) {
                // Candidate reads intentionally omit offers that submit validation would reject.
            }
            if (accepted.size() > limit) break;
        }

        boolean hasMore = accepted.size() > limit;
        List<NeedResponseCandidateResponse> items = hasMore
                ? List.copyOf(accepted.subList(0, limit)) : List.copyOf(accepted);
        String nextCursor = hasMore ? items.get(items.size() - 1).locationVariantId().toString() : null;
        return new NeedResponseCandidateCursorPage(items, hasMore, nextCursor, maxItems);
    }

    private static NeedResponseCandidateResponse toResponse(EligibleNeedResponseItem item) {
        return new NeedResponseCandidateResponse(
                item.locationVariantId(), item.catalogItemId(), item.variantId(), item.itemName(), item.variantName(),
                new NeedResponseItemResponse.MoneyResponse(item.currentPrice(), item.currency()), true,
                item.availabilitySnapshot());
    }

    private static UUID decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return UUID.fromString(cursor);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Candidate cursor is invalid.");
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
