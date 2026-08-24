package vn.covie.needs.application;

import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;

@Service
public class NeedScheduledTransitionService {
    private static final int MAX_BATCH_SIZE = 100;

    private final NeedRequestRepository needs;
    private final OutboxEventWriter outbox;
    private final AppClock clock;

    public NeedScheduledTransitionService(
            NeedRequestRepository needs,
            OutboxEventWriter outbox,
            AppClock clock) {
        this.needs = needs;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public int expireDueBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        Instant now = clock.now();
        var claimed = needs.claimExpiredBatch(now, limit);
        for (var need : claimed) {
            need.expire(now);
            outbox.append(new OutboxEventIntent(
                    "NEED_EXPIRED",
                    "NEED_REQUEST",
                    need.getId(),
                    Map.of(
                            "needRequestId", need.getId(),
                            "buyerUserId", need.getBuyerUserId())));
        }
        return claimed.size();
    }
}
