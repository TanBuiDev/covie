package vn.covie.needs.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.needs.api.dto.NeedCursorPage;
import vn.covie.needs.api.dto.NeedDetailResponse;
import vn.covie.needs.api.dto.NeedSummaryResponse;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedReadService {
    private static final int MAX_LIMIT = 100;
    private final CurrentActorProvider actors;
    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final AppClock clock;
    private final NeedTimestampCursorCodec cursors;

    public NeedReadService(CurrentActorProvider actors, NeedRequestRepository needs, NeedResponseRepository responses,
            AppClock clock, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.actors = actors; this.needs = needs; this.responses = responses; this.clock = clock;
        this.cursors = new NeedTimestampCursorCodec(mapper);
    }

    public NeedCursorPage list(NeedStatus requestedStatus, Integer requestedLimit, String cursor) {
        UUID buyerId = actors.currentActor().userId();
        int limit = requestedLimit == null ? 20 : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        NeedTimestampCursor position = cursors.decode(cursor);
        Instant now = clock.now(); List<NeedSummaryResponse> result = new ArrayList<>();
        for (NeedRequestEntity need : needs.findByBuyerUserIdOrderByCreatedAtDescIdDesc(buyerId)) {
            if (position != null && compare(need, position) >= 0) continue;
            NeedStatus effective = NeedCommandService.effectiveStatus(need, now);
            if (requestedStatus != null && effective != requestedStatus) continue;
            result.add(new NeedSummaryResponse(need.getId(), need.getNeedType(), effective, need.getCategoryId(), need.getTitle(),
                    need.getSearchRadiusMeters(), need.getExpiresAt(), countActiveResponses(need.getId()), need.getCreatedAt()));
            if (result.size() > limit) break;
        }
        boolean hasMore = result.size() > limit;
        if (hasMore) result.removeLast();
        String next = hasMore ? cursors.encode(new NeedTimestampCursor(result.getLast().createdAt(), result.getLast().id())) : null;
        return new NeedCursorPage(List.copyOf(result), hasMore, next);
    }

    public NeedDetailResponse detail(UUID needId) {
        UUID buyerId = actors.currentActor().userId();
        NeedRequestEntity need = needs.findByIdAndBuyerUserId(needId, buyerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        return NeedCommandService.detail(need, clock.now(), countActiveResponses(needId));
    }

    private long countActiveResponses(UUID needId) { return responses.countByNeedRequestIdAndResponseStatus(needId, NeedResponseStatus.ACTIVE); }
    private static int compare(NeedRequestEntity need, NeedTimestampCursor cursor) {
        int time = need.getCreatedAt().compareTo(cursor.createdAt());
        return time != 0 ? time : need.getId().compareTo(cursor.id());
    }
}
