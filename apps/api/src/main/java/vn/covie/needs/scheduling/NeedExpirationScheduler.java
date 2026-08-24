package vn.covie.needs.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.covie.needs.application.NeedScheduledTransitionService;

@Component
public class NeedExpirationScheduler {
    private static final int BATCH_SIZE = 50;

    private final NeedScheduledTransitionService scheduledTransitionService;

    public NeedExpirationScheduler(NeedScheduledTransitionService scheduledTransitionService) {
        this.scheduledTransitionService = scheduledTransitionService;
    }

    @Scheduled(fixedDelayString = "${covie.jobs.need-expiration-delay:PT5S}")
    public void tick() {
        scheduledTransitionService.expireDueBatch(BATCH_SIZE);
    }
}
