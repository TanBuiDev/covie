package vn.covie.auth.oauth;

import java.util.Locale;
import java.util.Set;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/** Configuration readiness only; it does not prove provider credentials are valid. */
@Component
public class GoogleOAuthAvailability {
    private final ClientRegistrationRepository registrations;

    public GoogleOAuthAvailability(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    public boolean configured() {
        var google = registrations.findByRegistrationId("google");
        return google != null && supplied(google.getClientId()) && supplied(google.getClientSecret())
                && google.getClientId().endsWith(".apps.googleusercontent.com");
    }

    private boolean supplied(String value) {
        if (value == null || value.isBlank()) return false;
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("disabled-") && !normalized.startsWith("your-")
                && !Set.of("changeme", "change-me", "replace-me").contains(normalized);
    }
}
