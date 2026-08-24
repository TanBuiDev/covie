package vn.covie.settings.outbox;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.outbox.application.OutboxEventHandler;
import vn.covie.realtime.SseSignal;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;

@Component
public class SystemSettingOutboxHandler implements OutboxEventHandler {
    private final SystemRoleAssignmentRepository roles;
    public SystemSettingOutboxHandler(SystemRoleAssignmentRepository roles) { this.roles = roles; }
    @Override public boolean supports(String eventType) { return "SYSTEM_SETTING_CHANGED".equals(eventType); }
    @Override public List<SseSignal> handle(OutboxEventContext event) {
        String key = event.payload().path("settingKey").asText(null);
        if (key == null || key.isBlank()) return List.of();
        return roles.findUserIdsByRole(SystemRole.ADMIN).stream()
                .map(userId -> new SseSignal(userId, "admin.setting.updated", Map.of("settingKey", key))).toList();
    }
}
