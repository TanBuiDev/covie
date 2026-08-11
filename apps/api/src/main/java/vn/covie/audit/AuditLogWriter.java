package vn.covie.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;

@Service
public class AuditLogWriter {
    private final AuditLogRepository repository;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AuditLogWriter(AuditLogRepository repository, UuidV7Generator ids, AppClock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void securityEvent(UUID actorUserId, String action, String entityType, UUID entityId, String requestId, Map<String, Object> metadata) {
        repository.save(AuditLogEntity.security(ids.generate(), actorUserId, action, entityType, entityId, requestId, OBJECT_MAPPER.valueToTree(metadata), clock.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void securityFailure(String action, String requestId, Map<String, Object> metadata) {
        repository.save(AuditLogEntity.security(ids.generate(), null, action, "AUTH", null, requestId, OBJECT_MAPPER.valueToTree(metadata), clock.now()));
    }
}
