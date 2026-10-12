package vn.covie.media.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.shared.time.AppClock;

@Service
public class MediaCleanupService {
    private final MediaAssetRepository repository;
    private final ObjectStorage storage;
    private final AppClock clock;
    private final TransactionTemplate transactions;
    public MediaCleanupService(MediaAssetRepository repository, ObjectStorage storage, AppClock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository; this.storage = storage; this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }
    public int cleanupExpiredPending(int limit) {
        if (limit <= 0) return 0;
        List<String> keys = transactions.execute(status -> claimExpired(limit));
        keys.forEach(key -> { try { storage.deleteIfExists(key); } catch (RuntimeException ignored) { } });
        return keys.size();
    }
    private List<String> claimExpired(int limit) {
        Instant now = clock.now();
        List<MediaAssetEntity> assets = repository.claimExpiredPending(now.minus(Duration.ofHours(24)), limit);
        assets.forEach(asset -> asset.markDeleted(now));
        repository.flush();
        return assets.stream().map(MediaAssetEntity::getStorageKey).toList();
    }
}
