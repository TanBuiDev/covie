package vn.covie.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;

class JwtTokenServiceTest {
    @Test
    void issuesRsaSignedAccessTokenWithRequiredClaims() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).keyID("test-key").build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        AuthProperties properties = properties();
        JwtTokenService service = new JwtTokenService(encoder, properties, () -> now, new UuidV7Generator());
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        IssuedAccessToken issued = service.issueAccessToken(userId, sessionId, Set.of(SystemRole.USER));
        Jwt decoded = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build().decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("sessionId")).isEqualTo(sessionId.toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(decoded.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(decoded.getId()).isNotBlank();
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresAt()).isEqualTo(decoded.getExpiresAt());
    }

    private AuthProperties properties() {
        return new AuthProperties("https://covie.test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10),
                new AuthProperties.Jwt("test-key", null, null),
                new AuthProperties.Cookies(false, "Lax", "covie_at", "covie_rt", "covie_auth_action"));
    }
}
