package vn.covie.admin.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.AdminCatalogCaseOpenedResponse;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.moderation.model.ModerationCaseEntity;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class AdminCatalogCaseOpeningService {
    private final AdminAccessPolicy access;
    private final CatalogItemRepository items;
    private final ModerationCaseRepository cases;
    private final JdbcTemplate jdbc;
    private final AuditLogWriter audit;
    private final OutboxEventWriter outbox;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final RequestIdAccessor requestIds;
    private final boolean enabled;

    public AdminCatalogCaseOpeningService(AdminAccessPolicy access, CatalogItemRepository items,
            ModerationCaseRepository cases, JdbcTemplate jdbc, AuditLogWriter audit,
            OutboxEventWriter outbox, UuidV7Generator ids, AppClock clock, RequestIdAccessor requestIds,
            @Value("${covie.social-v2.admin-case-opening-enabled:false}") boolean enabled) {
        this.access=access; this.items=items; this.cases=cases; this.jdbc=jdbc; this.audit=audit;
        this.outbox=outbox; this.ids=ids; this.clock=clock; this.requestIds=requestIds; this.enabled=enabled;
    }

    public void requireEnabled() {
        access.requireAdmin();
        if (!enabled) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");
    }

    @Transactional
    public AdminCatalogCaseOpenedResponse open(UUID itemId, AdminReasonRequest request) {
        requireEnabled();
        var actor=access.requireAdmin();
        var item=items.lockById(itemId).orElseThrow(() ->
                new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found."));
        var existing=jdbc.query("""
                SELECT id FROM moderation_cases
                WHERE target_catalog_item_id=? AND case_status='OPEN'
                ORDER BY opened_at,id LIMIT 1
                """, (rows,index) -> rows.getObject("id",UUID.class),itemId);
        if (!existing.isEmpty()) return new AdminCatalogCaseOpenedResponse(existing.getFirst(),false);
        if (item.getModerationStatus()!=CatalogModerationStatus.NOT_REQUIRED
                && item.getModerationStatus()!=CatalogModerationStatus.APPROVED) {
            throw new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT,HttpStatus.CONFLICT,
                    "The item cannot be moderated in its current state.");
        }
        var created=ModerationCaseEntity.systemFlag(ids.generate(),
                new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM,itemId),
                request.reasonCode().strip(),request.note()==null?null:request.note().strip(),clock.now());
        cases.saveAndFlush(created);
        audit.securityEvent(actor.userId(),"ADMIN_MODERATION_CASE_OPENED","MODERATION_CASE",created.getId(),
                requestIds.current(),Map.of("targetId",itemId.toString(),"reasonCode",created.getReasonCode()));
        outbox.append(new OutboxEventIntent("MODERATION_CASE_OPENED","MODERATION_CASE",created.getId(),
                Map.of("targetType",ModerationTargetType.CATALOG_ITEM.name(),"targetId",itemId.toString())));
        return new AdminCatalogCaseOpenedResponse(created.getId(),true);
    }
}
