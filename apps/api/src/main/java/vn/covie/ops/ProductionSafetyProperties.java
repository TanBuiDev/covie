package vn.covie.ops;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.production")
public record ProductionSafetyProperties(URI publicUrl, boolean e2eSupportEnabled) {}
