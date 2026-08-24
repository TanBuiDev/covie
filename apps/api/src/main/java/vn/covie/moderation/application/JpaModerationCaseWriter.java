package vn.covie.moderation.application;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.model.ModerationCaseStatus;
import vn.covie.moderation.model.ModerationCaseType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.moderation.ModerationCaseWriter;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;

@Service
public final class JpaModerationCaseWriter implements ModerationCaseWriter {
    private final EntityManager entityManager;
    private final ModerationCaseRepository cases;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final OutboxEventWriter outbox;

    public JpaModerationCaseWriter(
            EntityManager entityManager,
            ModerationCaseRepository cases,
            UuidV7Generator ids,
            AppClock clock,
            OutboxEventWriter outbox) {
        this.entityManager = entityManager;
        this.cases = cases;
        this.ids = ids;
        this.clock = clock;
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID openPreApproval(ModerationTargetRef target, String reasonCode) {
        if (target.type() != ModerationTargetType.CATALOG_ITEM && target.type() != ModerationTargetType.NEED_REQUEST) {
            throw new IllegalArgumentException("PRE_APPROVAL supports only catalog items and need requests in V1.");
        }
        UUID candidateId = ids.generate();
        Instant now = clock.now();
        String targetColumn = target.type() == ModerationTargetType.CATALOG_ITEM
                ? "target_catalog_item_id" : "target_need_request_id";
        String sql = "insert into moderation_cases "
                + "(id,case_type,target_type," + targetColumn + ",reason_code,case_status,opened_at) "
                + "values (:id,'PRE_APPROVAL',:targetType,:targetId,:reasonCode,'OPEN',:openedAt) "
                + "on conflict do nothing returning id";
        @SuppressWarnings("unchecked")
        List<UUID> inserted = entityManager.createNativeQuery(sql, UUID.class)
                .setParameter("id", candidateId)
                .setParameter("targetType", target.type().name())
                .setParameter("targetId", target.id())
                .setParameter("reasonCode", reasonCode)
                .setParameter("openedAt", now)
                .getResultList();
        if (!inserted.isEmpty()) {
            UUID caseId = inserted.getFirst();
            outbox.append(new OutboxEventIntent(
                    "MODERATION_CASE_OPENED",
                    "MODERATION_CASE",
                    caseId,
                    Map.of("targetType", target.type().name(), "targetId", target.id().toString())));
            return caseId;
        }
        return switch (target.type()) {
            case CATALOG_ITEM -> cases.findByCaseTypeAndCaseStatusAndTargetCatalogItemId(
                            ModerationCaseType.PRE_APPROVAL, ModerationCaseStatus.OPEN, target.id())
                    .orElseThrow(() -> new IllegalStateException("Concurrent catalog pre-approval insert did not converge."))
                    .getId();
            case NEED_REQUEST -> cases.findByCaseTypeAndCaseStatusAndTargetNeedRequestId(
                            ModerationCaseType.PRE_APPROVAL, ModerationCaseStatus.OPEN, target.id())
                    .orElseThrow(() -> new IllegalStateException("Concurrent need pre-approval insert did not converge."))
                    .getId();
            default -> throw new IllegalStateException("Unsupported pre-approval target.");
        };
    }
}
