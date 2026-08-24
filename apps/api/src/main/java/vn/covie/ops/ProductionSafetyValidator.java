package vn.covie.ops;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.covie.auth.security.AuthProperties;

@Component
@Profile("prod")
public final class ProductionSafetyValidator implements ApplicationRunner {
    private static final String GOOGLE_REDIRECT_PATH = "/api/v1/auth/google/callback/google";

    private final ProductionSafetyProperties production;
    private final AuthProperties auth;
    private final Environment environment;

    public ProductionSafetyValidator(ProductionSafetyProperties production, AuthProperties auth, Environment environment) {
        this.production = production;
        this.auth = auth;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireHttps("covie.production.public-url", production.publicUrl());
        requireHttps("covie.auth.issuer", uri("covie.auth.issuer", auth.issuer()));
        requireGoogleConfiguration();
        require(auth.cookies() != null && auth.cookies().secure(), "covie.auth.cookies.secure");
        requireReadableFile("covie.auth.jwt.private-key-path", auth.jwt() == null ? null : auth.jwt().privateKeyPath());
        requireReadableFile("covie.auth.jwt.public-key-path", auth.jwt() == null ? null : auth.jwt().publicKeyPath());
        require(Arrays.stream(environment.getActiveProfiles()).noneMatch("e2e"::equalsIgnoreCase), "spring.profiles.active");
        require(!production.e2eSupportEnabled(), "covie.production.e2e-support-enabled");
        require("validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto")), "spring.jpa.hibernate.ddl-auto");
        require(environment.getProperty("spring.flyway.enabled", Boolean.class, false), "spring.flyway.enabled");
    }

    private void requireGoogleConfiguration() {
        String clientId = environment.getProperty("spring.security.oauth2.client.registration.google.client-id");
        String clientSecret = environment.getProperty("spring.security.oauth2.client.registration.google.client-secret");
        String redirect = environment.getProperty("spring.security.oauth2.client.registration.google.redirect-uri");
        require(StringUtils.hasText(clientId) && !clientId.startsWith("disabled-local-"), "spring.security.oauth2.client.registration.google.client-id");
        require(StringUtils.hasText(clientSecret) && !clientSecret.startsWith("disabled-local-"), "spring.security.oauth2.client.registration.google.client-secret");
        URI expected = production.publicUrl().resolve(GOOGLE_REDIRECT_PATH);
        URI actual = uri("spring.security.oauth2.client.registration.google.redirect-uri", redirect);
        require(expected.equals(actual) && "https".equalsIgnoreCase(actual.getScheme()), "spring.security.oauth2.client.registration.google.redirect-uri");
    }

    private static URI uri(String name, String value) {
        try {
            return URI.create(value == null ? "" : value);
        } catch (IllegalArgumentException ex) {
            throw unsafe(name);
        }
    }

    private static void requireHttps(String name, URI uri) {
        require(uri != null && "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost()), name);
    }

    private static void requireReadableFile(String name, Path path) {
        require(path != null && Files.isRegularFile(path) && Files.isReadable(path), name);
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw unsafe(name);
    }

    private static IllegalStateException unsafe(String name) {
        return new IllegalStateException("Unsafe production configuration: " + name);
    }
}
