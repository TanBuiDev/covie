package vn.covie.admin.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminAuditLogResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.application.AdminPageRequestValidator;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogEntity;
import vn.covie.audit.AuditLogRepository;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public final class AdminAuditController {
    private static final Set<String> SORTS=Set.of("CREATED_DESC","CREATED_ASC");
    private final AdminAccessPolicy admins; private final AdminPageRequestValidator pages; private final AuditLogRepository audit;
    public AdminAuditController(AdminAccessPolicy admins,AdminPageRequestValidator pages,AuditLogRepository audit){this.admins=admins;this.pages=pages;this.audit=audit;}

    @GetMapping
    public ResponseEntity<AdminPageResponse<AdminAuditLogResponse>> list(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String sort,
            @RequestParam(required=false) UUID actorUserId,@RequestParam(required=false) String action,@RequestParam(required=false) String entityType,
            @RequestParam(required=false) UUID entityId,@RequestParam(required=false) String requestId,
            @RequestParam(required=false) Instant createdFrom,@RequestParam(required=false) Instant createdTo){
        admins.requireAdmin(); var p=pages.validate(page,size,sort,SORTS,"CREATED_DESC");
        Specification<AuditLogEntity> spec=Specification.where(null);
        if(actorUserId!=null) spec=spec.and((r,q,b)->b.equal(r.get("actorUserId"),actorUserId));
        if(action!=null&&!action.isBlank()) spec=spec.and((r,q,b)->b.equal(r.get("action"),action.strip()));
        if(entityType!=null&&!entityType.isBlank()) spec=spec.and((r,q,b)->b.equal(r.get("entityType"),entityType.strip()));
        if(entityId!=null) spec=spec.and((r,q,b)->b.equal(r.get("entityId"),entityId));
        if(requestId!=null&&!requestId.isBlank()) spec=spec.and((r,q,b)->b.equal(r.get("requestId"),requestId.strip()));
        if(createdFrom!=null) spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("createdAt"),createdFrom));
        if(createdTo!=null) spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("createdAt"),createdTo));
        Sort order="CREATED_ASC".equals(p.sort())?Sort.by("createdAt").ascending().and(Sort.by("id").ascending()):Sort.by("createdAt").descending().and(Sort.by("id").descending());
        var result=audit.findAll(spec,PageRequest.of(p.page(),p.size(),order));
        var body=new AdminPageResponse<>(result.getContent().stream().map(AdminAuditLogResponse::from).toList(),result.getTotalElements(),p.page(),p.size(),result.getTotalPages());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);
    }
}
