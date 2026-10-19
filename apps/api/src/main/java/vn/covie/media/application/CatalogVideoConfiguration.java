package vn.covie.media.application;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogVideoConfiguration {
    @Bean CatalogVideoProbe catalogVideoProbe(
            @Value("${covie.social-v2.video-ffprobe:ffprobe}") String ffprobe,
            @Value("${covie.social-v2.video-ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${covie.social-v2.video-probe-timeout:PT20S}") String timeout) {
        return new CatalogVideoProbe(Path.of(ffprobe), Path.of(ffmpeg), Duration.parse(timeout));
    }
}
