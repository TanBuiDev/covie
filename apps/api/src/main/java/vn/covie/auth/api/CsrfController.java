package vn.covie.auth.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.api.dto.CsrfTokenResponse;

@RestController @RequestMapping("/api/v1/auth")
public class CsrfController {
    @GetMapping("/csrf") public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CsrfTokenResponse(token.getHeaderName(), token.getToken())); }
}
