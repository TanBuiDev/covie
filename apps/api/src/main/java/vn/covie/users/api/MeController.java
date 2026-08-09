package vn.covie.users.api;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.users.api.dto.MeResponse;
import vn.covie.users.api.dto.UpdateMeRequest;
import vn.covie.users.application.UserProfileService;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile")
@SecurityRequirement(name = "bearerAuth")
public class MeController {
    private final UserProfileService profiles;
    public MeController(UserProfileService profiles) { this.profiles = profiles; }
    @GetMapping @Operation(summary = "Get the authenticated profile") public MeResponse me() { return profiles.me(); }
    @PatchMapping @Operation(summary = "Update the authenticated profile", description = "Browser mutations use X-CSRF-Token.") public ResponseEntity<MeResponse> update(@Valid @RequestBody UpdateMeRequest request) { return ResponseEntity.ok(profiles.update(request.displayName())); }
}
