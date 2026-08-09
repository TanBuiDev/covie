package vn.covie.users.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.users.api.dto.ContactMethodResponse;
import vn.covie.users.api.dto.CreateContactMethodRequest;
import vn.covie.users.api.dto.UpdateContactMethodRequest;
import vn.covie.users.application.UserContactMethodService;

@RestController
@RequestMapping("/api/v1/me/contact-methods")
@Tag(name = "Contact methods")
@SecurityRequirement(name = "bearerAuth")
public class UserContactMethodController {
    private final UserContactMethodService contacts;
    public UserContactMethodController(UserContactMethodService contacts) { this.contacts = contacts; }
    @GetMapping @Operation(summary = "List contact methods") public List<ContactMethodResponse> active() { return contacts.active().stream().map(ContactMethodResponse::from).toList(); }
    @PostMapping public ResponseEntity<ContactMethodResponse> create(@Valid @RequestBody CreateContactMethodRequest request) { return ResponseEntity.ok(ContactMethodResponse.from(contacts.create(request.type(), request.value(), request.primary()))); }
    @PatchMapping("/{contactMethodId}") public ContactMethodResponse update(@PathVariable UUID contactMethodId, @Valid @RequestBody UpdateContactMethodRequest request) { return ContactMethodResponse.from(contacts.update(contactMethodId, request.value(), request.primary())); }
    @DeleteMapping("/{contactMethodId}") public ResponseEntity<Void> delete(@PathVariable UUID contactMethodId) { contacts.delete(contactMethodId); return ResponseEntity.noContent().build(); }
}
