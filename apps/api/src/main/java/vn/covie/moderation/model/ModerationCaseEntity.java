package vn.covie.moderation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.covie.shared.moderation.ModerationTargetRef;

@Entity
@Table(name = "moderation_cases")
public class ModerationCaseEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "case_type", nullable = false, length = 30) private ModerationCaseType caseType;
    @Column(name = "reporter_user_id") private UUID reporterUserId;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 30) private ModerationTargetType targetType;
    @Column(name = "target_user_id") private UUID targetUserId;
    @Column(name = "target_merchant_profile_id") private UUID targetMerchantProfileId;
    @Column(name = "target_catalog_item_id") private UUID targetCatalogItemId;
    @Column(name = "target_need_request_id") private UUID targetNeedRequestId;
    @Column(name = "target_review_id") private UUID targetReviewId;
    @Column(name = "reason_code", length = 60) private String reasonCode;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "case_status", nullable = false, length = 30) private ModerationCaseStatus caseStatus;
    @Column(name = "assigned_admin_user_id") private UUID assignedAdminUserId;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;

    protected ModerationCaseEntity() {}

    public static ModerationCaseEntity preApprovalCatalog(UUID id, UUID catalogItemId, Instant now) {
        return create(id, ModerationCaseType.PRE_APPROVAL, null,
                new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM, catalogItemId), null, null, now);
    }

    public static ModerationCaseEntity preApprovalNeed(UUID id, UUID needRequestId, Instant now) {
        return create(id, ModerationCaseType.PRE_APPROVAL, null,
                new ModerationTargetRef(ModerationTargetType.NEED_REQUEST, needRequestId), null, null, now);
    }

    public static ModerationCaseEntity userReport(
            UUID id, UUID reporterUserId, ModerationTargetRef target,
            String reasonCode, String description, Instant now) {
        return create(id, ModerationCaseType.USER_REPORT, reporterUserId, target, reasonCode, description, now);
    }

    private static ModerationCaseEntity create(
            UUID id, ModerationCaseType caseType, UUID reporterUserId, ModerationTargetRef target,
            String reasonCode, String description, Instant now) {
        ModerationCaseEntity entity = new ModerationCaseEntity();
        entity.id = id;
        entity.caseType = caseType;
        entity.reporterUserId = reporterUserId;
        entity.targetType = target.type();
        switch (target.type()) {
            case USER -> entity.targetUserId = target.id();
            case MERCHANT -> entity.targetMerchantProfileId = target.id();
            case CATALOG_ITEM -> entity.targetCatalogItemId = target.id();
            case NEED_REQUEST -> entity.targetNeedRequestId = target.id();
            case REVIEW -> entity.targetReviewId = target.id();
        }
        entity.reasonCode = normalizeOptional(reasonCode);
        entity.description = normalizeOptional(description);
        entity.caseStatus = ModerationCaseStatus.OPEN;
        entity.openedAt = now;
        return entity;
    }

    public void resolve(Instant now) {
        if (caseStatus != ModerationCaseStatus.OPEN) throw new IllegalStateException("Moderation case is not OPEN.");
        caseStatus = ModerationCaseStatus.RESOLVED;
        resolvedAt = now;
    }

    public void close(Instant now) {
        if (caseStatus != ModerationCaseStatus.OPEN) throw new IllegalStateException("Moderation case is not OPEN.");
        caseStatus = ModerationCaseStatus.CLOSED;
        closedAt = now;
    }

    public boolean isOpen() { return caseStatus == ModerationCaseStatus.OPEN; }

    public ModerationTargetRef targetRef() {
        UUID targetId = switch (targetType) {
            case USER -> targetUserId;
            case MERCHANT -> targetMerchantProfileId;
            case CATALOG_ITEM -> targetCatalogItemId;
            case NEED_REQUEST -> targetNeedRequestId;
            case REVIEW -> targetReviewId;
        };
        return new ModerationTargetRef(targetType, targetId);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public ModerationCaseType getCaseType() { return caseType; }
    public UUID getReporterUserId() { return reporterUserId; }
    public ModerationTargetType getTargetType() { return targetType; }
    public UUID getTargetUserId() { return targetUserId; }
    public UUID getTargetMerchantProfileId() { return targetMerchantProfileId; }
    public UUID getTargetCatalogItemId() { return targetCatalogItemId; }
    public UUID getTargetNeedRequestId() { return targetNeedRequestId; }
    public UUID getTargetReviewId() { return targetReviewId; }
    public String getReasonCode() { return reasonCode; }
    public String getDescription() { return description; }
    public ModerationCaseStatus getCaseStatus() { return caseStatus; }
    public UUID getAssignedAdminUserId() { return assignedAdminUserId; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
}
