package vn.covie.auth.api;

import org.springframework.http.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.api.dto.CsrfTokenResponse;

@RestController @RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class CsrfController {
    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token", description = "Browser mutations use X-CSRF-Token. Browser auth cookies are HttpOnly.")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CsrfTokenResponse(token.getHeaderName(), token.getToken())); }
}
