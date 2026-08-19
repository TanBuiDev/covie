package vn.covie.media.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3StorageConfiguration {

    @Bean
    S3Configuration s3Configuration(StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.forcePathStyle())
                .build();
    }

    @Bean
    S3Client s3Client(StorageProperties properties, S3Configuration s3Configuration) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .serviceConfiguration(s3Configuration);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties, S3Configuration s3Configuration) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .serviceConfiguration(s3Configuration);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }
}
