package vn.covie.users.security;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
