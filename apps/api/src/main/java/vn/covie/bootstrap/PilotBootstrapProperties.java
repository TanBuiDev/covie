package vn.covie.bootstrap;

import org.springframework.core.env.Environment;

public record PilotBootstrapProperties(
        boolean referenceDataEnabled,
        String referenceDataPath,
        String firstAdminEmail) {

    static PilotBootstrapProperties from(Environment environment) {
        return new PilotBootstrapProperties(
                environment.getProperty("covie.bootstrap.reference-data.enabled", Boolean.class, false),
                environment.getProperty(
                        "covie.bootstrap.reference-data.path",
                        "classpath:/bootstrap/grand-park-reference-data.json"),
                environment.getProperty("covie.bootstrap.first-admin-email", ""));
    }
}
