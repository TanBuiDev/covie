package vn.covie.admin.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminAuditLogResponse;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogEntity;
import vn.covie.audit.AuditLogRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {
    private final AdminAccessPolicy access;
    private final AuditLogRepository audit;

    public AdminAuditController(AdminAccessPolicy access, AuditLogRepository audit) {
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<Page<AdminAuditLogResponse>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "CREATED_DESC") String sort,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo) {
        access.requireAdmin();
        if (page < 0 || size < 1 || size > 100 || !(sort.equals("CREATED_DESC") || sort.equals("CREATED_ASC"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Audit page, size or sort is invalid.");
        }
        Sort.Direction direction = sort.equals("CREATED_ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;

        Specification<AuditLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorUserId != null) predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            String act = blank(action);
            if (act != null) predicates.add(cb.equal(root.get("action"), act));
            String entType = blank(entityType);
            if (entType != null) predicates.add(cb.equal(root.get("entityType"), entType));
            if (entityId != null) predicates.add(cb.equal(root.get("entityId"), entityId));
            String reqId = blank(requestId);
            if (reqId != null) predicates.add(cb.equal(root.get("requestId"), reqId));
            if (createdFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            if (createdTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AdminAuditLogResponse> result = audit.findAll(spec, PageRequest.of(page, size, Sort.by(direction, "createdAt", "id")))
                .map(a -> new AdminAuditLogResponse(a.getId(), a.getActorUserId(), a.getActorType(), a.getEventCategory(),
                        a.getAction(), a.getEntityType(), a.getEntityId(), vn.covie.shared.json.JsonValues.unwrap(a.getAfterSnapshot()), a.getRequestId(), a.getCreatedAt()));
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(result);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
