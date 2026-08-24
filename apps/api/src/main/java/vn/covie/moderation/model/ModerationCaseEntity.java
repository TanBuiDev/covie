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
    @Enumerated(EnumType.STRING) @Column(name = "case_type", nullable = false) private ModerationCaseType caseType;
    @Column(name = "reporter_user_id") private UUID reporterUserId;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false) private ModerationTargetType targetType;
    @Column(name = "target_user_id") private UUID targetUserId;
    @Column(name = "target_merchant_profile_id") private UUID targetMerchantProfileId;
    @Column(name = "target_catalog_item_id") private UUID targetCatalogItemId;
    @Column(name = "target_need_request_id") private UUID targetNeedRequestId;
    @Column(name = "target_review_id") private UUID targetReviewId;
    @Column(name = "reason_code") private String reasonCode;
    private String description;
    @Enumerated(EnumType.STRING) @Column(name = "case_status", nullable = false) private ModerationCaseStatus caseStatus;
    @Column(name = "assigned_admin_user_id") private UUID assignedAdminUserId;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    protected ModerationCaseEntity() {}
    public static ModerationCaseEntity preApprovalCatalog(UUID id, UUID catalogItemId, Instant now) { return create(id, ModerationCaseType.PRE_APPROVAL, null, new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM, catalogItemId), null, null, now); }
    public static ModerationCaseEntity preApprovalNeed(UUID id, UUID needRequestId, Instant now) { return create(id, ModerationCaseType.PRE_APPROVAL, null, new ModerationTargetRef(ModerationTargetType.NEED_REQUEST, needRequestId), null, null, now); }
    public static ModerationCaseEntity userReport(UUID id, UUID reporterUserId, ModerationTargetRef target, String reasonCode, String description, Instant now) { return create(id, ModerationCaseType.USER_REPORT, reporterUserId, target, reasonCode, description, now); }
    private static ModerationCaseEntity create(UUID id, ModerationCaseType type, UUID reporter, ModerationTargetRef target, String reason, String description, Instant now) {
        ModerationCaseEntity moderationCase = new ModerationCaseEntity(); moderationCase.id = id; moderationCase.caseType = type; moderationCase.reporterUserId = reporter; moderationCase.targetType = target.type(); moderationCase.reasonCode = reason; moderationCase.description = description; moderationCase.caseStatus = ModerationCaseStatus.OPEN; moderationCase.openedAt = now;
        switch (target.type()) { case USER -> moderationCase.targetUserId = target.id(); case MERCHANT -> moderationCase.targetMerchantProfileId = target.id(); case CATALOG_ITEM -> moderationCase.targetCatalogItemId = target.id(); case NEED_REQUEST -> moderationCase.targetNeedRequestId = target.id(); case REVIEW -> moderationCase.targetReviewId = target.id(); }
        return moderationCase;
    }
    public void resolve(Instant now) { if (!isOpen()) throw new IllegalStateException("Only open cases can be resolved."); caseStatus = ModerationCaseStatus.RESOLVED; resolvedAt = now; }
    public void close(Instant now) { if (caseStatus == ModerationCaseStatus.CLOSED) throw new IllegalStateException("Case is already closed."); caseStatus = ModerationCaseStatus.CLOSED; closedAt = now; }
    public boolean isOpen() { return caseStatus == ModerationCaseStatus.OPEN; }
    public ModerationTargetRef target() { return switch (targetType) { case USER -> new ModerationTargetRef(targetType, targetUserId); case MERCHANT -> new ModerationTargetRef(targetType, targetMerchantProfileId); case CATALOG_ITEM -> new ModerationTargetRef(targetType, targetCatalogItemId); case NEED_REQUEST -> new ModerationTargetRef(targetType, targetNeedRequestId); case REVIEW -> new ModerationTargetRef(targetType, targetReviewId); }; }
    public UUID getId() { return id; } public ModerationCaseType getCaseType() { return caseType; } public UUID getReporterUserId() { return reporterUserId; } public ModerationTargetType getTargetType() { return targetType; } public UUID getTargetUserId() { return targetUserId; } public UUID getTargetMerchantProfileId() { return targetMerchantProfileId; } public UUID getTargetCatalogItemId() { return targetCatalogItemId; } public UUID getTargetNeedRequestId() { return targetNeedRequestId; } public UUID getTargetReviewId() { return targetReviewId; } public String getReasonCode() { return reasonCode; } public String getDescription() { return description; } public ModerationCaseStatus getCaseStatus() { return caseStatus; } public UUID getAssignedAdminUserId() { return assignedAdminUserId; } public Instant getOpenedAt() { return openedAt; } public Instant getResolvedAt() { return resolvedAt; } public Instant getClosedAt() { return closedAt; }
}
