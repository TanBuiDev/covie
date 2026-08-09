package vn.covie.auth.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.api.dto.AuthSessionSummaryResponse;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/me/security/sessions")
public class AuthSessionController {
    private final SessionManagementService sessions; private final CurrentActorProvider actorProvider;
    public AuthSessionController(SessionManagementService sessions, CurrentActorProvider actorProvider) { this.sessions=sessions; this.actorProvider=actorProvider; }
    @GetMapping public List<AuthSessionSummaryResponse> list() { CurrentActor actor=actorProvider.currentActor(); return sessions.activeSessions(actor.userId()).stream().map(session -> AuthSessionSummaryResponse.from(session, actor.sessionId())).toList(); }
    @DeleteMapping("/{sessionId}") public ResponseEntity<Void> revoke(@PathVariable UUID sessionId) { CurrentActor actor=actorProvider.currentActor(); sessions.revokeSession(actor.userId(), sessionId, vn.covie.auth.model.AuthSessionRevocationReason.USER_REVOKED); return ResponseEntity.noContent().build(); }
    @PostMapping("/revoke-others") public ResponseEntity<Void> revokeOthers() { CurrentActor actor=actorProvider.currentActor(); sessions.revokeOthers(actor.userId(), actor.sessionId()); return ResponseEntity.noContent().build(); }
}
