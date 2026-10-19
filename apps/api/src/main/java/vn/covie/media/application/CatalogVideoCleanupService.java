package vn.covie.media.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.ops.OperationalMetrics;
import vn.covie.ops.ScheduledJobMetrics;
import vn.covie.retention.TechnicalRetentionProperties;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogVideoCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogVideoCleanupService.class);
    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final AppClock clock;
    private final TechnicalRetentionProperties retention;
    private final OperationalMetrics metrics;
    private final ScheduledJobMetrics jobs;
    private final TransactionTemplate transactions;
    private final Duration deleteTimeout;
    private final Duration lease;

    public CatalogVideoCleanupService(MediaAssetRepository assets, ObjectStorage storage, AppClock clock,
            TechnicalRetentionProperties retention, OperationalMetrics metrics, ScheduledJobMetrics jobs,
            PlatformTransactionManager transactionManager,
            @Value("${covie.social-v2.video-cleanup-timeout:PT20S}") String deleteTimeout) {
        this.assets=assets; this.storage=storage; this.clock=clock; this.retention=retention; this.metrics=metrics; this.jobs=jobs;
        this.transactions=new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.deleteTimeout=Duration.parse(deleteTimeout);
        if (this.deleteTimeout.isZero() || this.deleteTimeout.isNegative()) throw new IllegalArgumentException("video-cleanup-timeout must be positive");
        this.deleteTimeout.toNanos();
        this.lease=this.deleteTimeout.multipliedBy(2);
    }

    @Scheduled(initialDelayString="${covie.retention.initial-delay:PT2M}", fixedDelayString="${covie.retention.interval:PT15M}")
    public void scheduledSweep() {
        if (!retention.enabled()) return;
        try {
            jobs.run(ScheduledJobMetrics.Job.CATALOG_VIDEO_CLEANUP, () -> cleanup(retention.batchSize()));
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Catalog video cleanup deferred after a failed sweep.");
        }
    }

    public int cleanup(int limit) {
        int deleted=0;
        for (int index=0;index<limit;index++) {
            CleanupTarget target=transactions.execute(status -> claim());
            if (target==null) break;
            try {
                storage.deleteIfExists(target.key(),deleteTimeout);
            } catch (RuntimeException unavailable) {
                throw new ApiException(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED,HttpStatus.SERVICE_UNAVAILABLE,
                        "Video object cleanup is unavailable. Retry later.");
            }
            Integer completed=transactions.execute(status -> assets.completeVideoStorageDeletion(target.id(),target.attempt(),now()));
            if (completed!=null && completed>0) {
                deleted+=completed;
                metrics.retentionDeleted("catalog_video_objects",completed);
            }
        }
        return deleted;
    }

    private CleanupTarget claim() {
        Instant now=now();
        Duration grace=retention.expiredRecordGrace().isNegative() ? Duration.ZERO : retention.expiredRecordGrace();
        var candidates=assets.claimExpiredCatalogVideo(now.minus(grace),now.minus(lease));
        if (candidates.isEmpty()) return null;
        var asset=candidates.getFirst();
        if (assets.hasCatalogReference(asset.getId())) return null;
        asset.claimVideoCleanup(now);
        assets.flush();
        return new CleanupTarget(asset.getId(),asset.getStorageKey(),now);
    }

    private Instant now() { return clock.now().truncatedTo(ChronoUnit.MICROS); }
    private record CleanupTarget(UUID id,String key,Instant attempt) {}
}
