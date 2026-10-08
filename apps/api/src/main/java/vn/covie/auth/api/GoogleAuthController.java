package vn.covie.auth.api;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.api.dto.GoogleAuthStatusResponse;
import vn.covie.auth.oauth.GoogleAuthIntent;
import vn.covie.auth.oauth.GoogleOAuthAvailability;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
    public static final String SESSION_INTENT = "covie.google.auth.intent";
    private final GoogleOAuthAvailability availability;

    public GoogleAuthController(GoogleOAuthAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/status")
    public GoogleAuthStatusResponse status() {
        return new GoogleAuthStatusResponse(availability.configured());
    }

    @GetMapping("/start")
    public void start(@RequestParam(defaultValue="LOGIN") GoogleAuthIntent intent,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!availability.configured()) {
            throw new ApiException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Google authentication is not configured.");
        }
        request.getSession(true).setAttribute(SESSION_INTENT, intent.name());
        response.sendRedirect("/api/v1/auth/oauth2/authorization/google");
    }
}
