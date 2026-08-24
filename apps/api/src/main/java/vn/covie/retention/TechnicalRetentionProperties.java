package vn.covie.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.retention")
public record TechnicalRetentionProperties(
        boolean enabled,
        int batchSize,
        Duration expiredRecordGrace,
        Duration publishedOutboxRetention) {
    public TechnicalRetentionProperties {
        if (batchSize <= 0) batchSize = 500;
        if (expiredRecordGrace == null) expiredRecordGrace = Duration.ofDays(7);
        if (publishedOutboxRetention == null) publishedOutboxRetention = Duration.ofDays(30);
    }
}
