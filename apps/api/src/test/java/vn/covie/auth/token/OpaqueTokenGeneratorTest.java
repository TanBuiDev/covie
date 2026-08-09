package vn.covie.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class OpaqueTokenGeneratorTest {
    private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();
    private final TokenFingerprint fingerprint = new TokenFingerprint();

    @Test
    void generatesDifferentBase64UrlTokensWith256BitsOfEntropy() {
        String first = generator.generate256BitToken();
        String second = generator.generate256BitToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
    }

    @Test
    void fingerprintsTokensDeterministicallyWithoutCollapsingDifferentValues() {
        assertThat(fingerprint.sha256Hex("token-one")).isEqualTo(fingerprint.sha256Hex("token-one"));
        assertThat(fingerprint.sha256Hex("token-one")).isNotEqualTo(fingerprint.sha256Hex("token-two"));
    }
}
