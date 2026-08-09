package vn.covie.users.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigurationTest {
    private final PasswordEncoder encoder = new PasswordConfiguration().passwordEncoder();

    @Test
    void hashesWithVersionedArgon2idPrefix() {
        String encoded = encoder.encode("correct horse battery staple");

        assertThat(encoded).startsWith("{argon2id-v1}");
        assertThat(encoder.matches("correct horse battery staple", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}
