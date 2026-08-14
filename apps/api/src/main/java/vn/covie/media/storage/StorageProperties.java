package vn.covie.media.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.storage")
public record StorageProperties(
        String bucket,
        String region,
        URI endpoint,
        URI publicBaseUrl,
        boolean forcePathStyle,
        Duration uploadUrlTtl,
        long maxImageBytes,
        int maxPendingUploadsPerUser) {}
