package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.ops.OperationalMetrics;
import vn.covie.ops.ScheduledJobMetrics;
import vn.covie.retention.TechnicalRetentionProperties;
import vn.covie.shared.time.AppClock;

class CatalogVideoCleanupClaimTest {
    @Test void candidateMustStillBeUnreferencedAfterItsAssetLockIsAcquired() {
        var assets=mock(MediaAssetRepository.class);
        var storage=mock(ObjectStorage.class);
        var clock=mock(AppClock.class);
        var transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Instant now=Instant.now(),old=now.minus(Duration.ofDays(30));
        when(clock.now()).thenReturn(now);
        var asset=MediaAssetEntity.pending(UUID.randomUUID(),UUID.randomUUID(),"owned/video",MediaPurpose.CATALOG_VIDEO,"video/mp4",24,old,old);
        asset.recordVideoMetadata(64,48,1000,"h264"); asset.markReady(old);
        when(assets.claimExpiredCatalogVideo(any(),any())).thenReturn(List.of(asset));
        when(assets.hasCatalogReference(asset.getId())).thenReturn(true);
        when(assets.completeVideoStorageDeletion(any(),any(),any())).thenReturn(1);
        var service=new CatalogVideoCleanupService(assets,storage,clock,
                new TechnicalRetentionProperties(false,10,Duration.ofDays(7),Duration.ofDays(30)),
                mock(OperationalMetrics.class),mock(ScheduledJobMetrics.class),transactions,"PT20S");
        service.scheduledSweep();
        verifyNoInteractions(storage);
        assertThat(service.cleanup(1)).isZero();
        assertThat(asset.getMediaStatus()).isEqualTo(MediaStatus.READY);
        verifyNoInteractions(storage);
    }
}
