package vn.covie.needs.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.covie.needs.application.NeedScheduledTransitionService;

@Component
public class NeedExpirationScheduler {
    private static final int BATCH_SIZE = 50;

    private final NeedScheduledTransitionService scheduledTransitionService;
    private final vn.covie.ops.ScheduledJobMetrics metrics;

    public NeedExpirationScheduler(NeedScheduledTransitionService scheduledTransitionService, vn.covie.ops.ScheduledJobMetrics metrics) {
        this.scheduledTransitionService = scheduledTransitionService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${covie.jobs.need-expiration-delay:PT5S}")
    public void tick() {
        metrics.run(vn.covie.ops.ScheduledJobMetrics.Job.NEEDS, () -> scheduledTransitionService.expireDueBatch(BATCH_SIZE));
    }
}
