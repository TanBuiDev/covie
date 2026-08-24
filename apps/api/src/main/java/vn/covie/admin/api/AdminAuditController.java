package vn.covie.admin.api;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminAuditLogResponse;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {
    private final AdminAccessPolicy access; private final AuditLogRepository audit;
    public AdminAuditController(AdminAccessPolicy access, AuditLogRepository audit) { this.access=access;this.audit=audit; }
    @GetMapping public ResponseEntity<Page<AdminAuditLogResponse>> page(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="CREATED_DESC") String sort,@RequestParam(required=false) UUID actorUserId,@RequestParam(required=false) String action,@RequestParam(required=false) String entityType,@RequestParam(required=false) UUID entityId,@RequestParam(required=false) String requestId,@RequestParam(required=false) Instant createdFrom,@RequestParam(required=false) Instant createdTo) {
        access.requireAdmin(); if(page<0||size<1||size>100||!(sort.equals("CREATED_DESC")||sort.equals("CREATED_ASC"))) throw new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"Audit page, size or sort is invalid.");
        Sort.Direction direction=sort.equals("CREATED_ASC")?Sort.Direction.ASC:Sort.Direction.DESC;
        Page<AdminAuditLogResponse> result=audit.findAdminPage(actorUserId,blank(action),blank(entityType),entityId,blank(requestId),createdFrom,createdTo,PageRequest.of(page,size,Sort.by(direction,"createdAt"))).map(a->new AdminAuditLogResponse(a.getId(),a.getActorUserId(),a.getActorType(),a.getEventCategory(),a.getAction(),a.getEntityType(),a.getEntityId(),a.getAfterSnapshot(),a.getRequestId(),a.getCreatedAt()));
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);
    }
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
