package vn.covie.media.storage;

import java.time.Duration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public final class ProductionStorageValidator implements ApplicationRunner {
    private final StorageProperties properties;

    public ProductionStorageValidator(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate(properties);
    }

    static void validate(StorageProperties p) {
        require(StringUtils.hasText(p.bucket()), "covie.storage.bucket");
        require(StringUtils.hasText(p.region()), "covie.storage.region");
        require(p.publicBaseUrl() != null && "https".equalsIgnoreCase(p.publicBaseUrl().getScheme())
                && StringUtils.hasText(p.publicBaseUrl().getHost()), "covie.storage.public-base-url");
        require(p.uploadUrlTtl() != null && !p.uploadUrlTtl().isNegative() && !p.uploadUrlTtl().isZero() && p.uploadUrlTtl().compareTo(Duration.ofMinutes(10)) <= 0, "covie.storage.upload-url-ttl");
        require(p.maxImageBytes() > 0 && p.maxImageBytes() <= 10L * 1024 * 1024, "covie.storage.max-image-bytes");
        if (p.endpoint() != null) {
            String host = p.endpoint().getHost();
            require("https".equalsIgnoreCase(p.endpoint().getScheme()), "covie.storage.endpoint");
            require(host != null && !host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1")
                    && !ipv6Loopback(host), "covie.storage.endpoint");
        }
    }

    private static boolean ipv6Loopback(String host) {
        if (!host.contains(":")) return false;
        // URI.getHost retains brackets for IPv6 literals; parsing a literal does not perform DNS lookup.
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try {
            return java.net.InetAddress.getByName(literal).isLoopbackAddress();
        } catch (java.net.UnknownHostException invalidLiteral) {
            return true;
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("Unsafe production configuration: " + name);
    }
}
