package vn.covie.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;

class JwtConfigurationTest {
    @TempDir Path directory;

    @Test
    void reloadingConfiguredPemKeysStillVerifiesTokensIssuedBeforeReload() throws Exception {
        var generated = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Path privateFile = directory.resolve("private.pem"), publicFile = directory.resolve("public.pem");
        Files.writeString(privateFile, pem("PRIVATE KEY", generated.getPrivate().getEncoded()));
        Files.writeString(publicFile, pem("PUBLIC KEY", generated.getPublic().getEncoded()));
        var properties = new AuthProperties("https://covie.test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10),
                new AuthProperties.Jwt("persistent-key", privateFile, publicFile),
                new AuthProperties.Cookies(false, "Lax", "covie_at", "covie_rt", "covie_auth_action"));
        var before = new JwtConfiguration();
        var loaded = before.configuredJwtKeyPair(properties);
        var service = new JwtTokenService(before.jwtEncoder(loaded, properties), properties, Instant::now, new UuidV7Generator());
        UUID user = UUID.randomUUID();
        var issued = service.issueAccessToken(user, UUID.randomUUID(), Set.of(SystemRole.USER));
        var after = new JwtConfiguration();
        var reloaded = after.configuredJwtKeyPair(properties);
        assertThat(after.jwtDecoder(reloaded, properties).decode(issued.value()).getSubject()).isEqualTo(user.toString());
        assertThat(reloaded.publicKey().getEncoded()).isEqualTo(loaded.publicKey().getEncoded());
    }

    private static String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(value) + "\n-----END " + type + "-----\n";
    }
}
