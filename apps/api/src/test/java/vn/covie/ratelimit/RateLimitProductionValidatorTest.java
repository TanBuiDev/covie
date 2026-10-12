package vn.covie.ratelimit;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;

class RateLimitProductionValidatorTest {
    @Test
    void acceptsAtLeast32ActualKeyBytesInBothSupportedEncodings() {
        assertThatCode(() -> validate("T".repeat(32))).doesNotThrowAnyException();
        assertThatCode(() -> validate("base64:" + Base64.getEncoder().encodeToString(new byte[32])))
                .doesNotThrowAnyException();
    }

    @Test
    void encodedLengthCannotHideAnUndersizedDecodedKey() {
        String encoded = "base64:" + Base64.getEncoder().encodeToString(new byte[24]);
        assertThat(encoded.length()).isGreaterThanOrEqualTo(32);
        assertThatThrownBy(() -> validate(encoded)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsafe production configuration: covie.rate-limit.hmac-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"base64:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", "short-key"})
    void rejectsInvalidKeysWithoutEchoingConfiguredValue(String key) {
        assertThatThrownBy(() -> validate(key)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsafe production configuration: covie.rate-limit.hmac-key");
    }

    private void validate(String key) {
        var policies = new EnumMap<RateLimitGroup, RateLimitPolicy>(RateLimitGroup.class);
        for (var group : RateLimitGroup.values()) {
            policies.put(group, new RateLimitPolicy(1, 1, Duration.ofHours(1), true));
        }
        new RateLimitProductionValidator(new RateLimitProperties(true, key, policies))
                .run(new DefaultApplicationArguments());
    }
}
