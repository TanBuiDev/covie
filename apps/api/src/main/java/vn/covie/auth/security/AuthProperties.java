package vn.covie.auth.security;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration actionTicketTtl,
        Jwt jwt,
        Cookies cookies) {
    public record Jwt(String keyId, Path privateKeyPath, Path publicKeyPath) {}
    public record Cookies(boolean secure, String sameSite, String accessName, String refreshName, String actionName) {}
}
