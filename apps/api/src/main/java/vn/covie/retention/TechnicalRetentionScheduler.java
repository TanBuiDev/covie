package vn.covie.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class TechnicalRetentionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicalRetentionScheduler.class);
    private final TechnicalRetentionProperties properties;
    private final TechnicalRetentionService service;
    private final vn.covie.ops.ScheduledJobMetrics metrics;

    public TechnicalRetentionScheduler(TechnicalRetentionProperties properties, TechnicalRetentionService service, vn.covie.ops.ScheduledJobMetrics metrics) {
        this.properties = properties;
        this.service = service;
        this.metrics = metrics;
    }

    @Scheduled(initialDelayString = "${covie.retention.initial-delay:PT2M}", fixedDelayString = "${covie.retention.interval:PT15M}")
    void run() {
        if (!properties.enabled()) return;
        try {
            metrics.run(vn.covie.ops.ScheduledJobMetrics.Job.RETENTION, () -> service.sweepOnce());
        } catch (RuntimeException ex) {
            LOGGER.error("Technical retention sweep failed", ex);
        }
    }
}
