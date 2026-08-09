package vn.covie.users.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {
    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void trimsAndLowercasesEmailUsingRootLocale() {
        assertThat(normalizer.normalize("  User.Name@Example.COM  ")).isEqualTo("user.name@example.com");
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> normalizer.normalize("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
