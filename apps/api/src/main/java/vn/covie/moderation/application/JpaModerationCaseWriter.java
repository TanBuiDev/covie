package vn.covie.moderation.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.model.ModerationCaseEntity;
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
public class JpaModerationCaseWriter implements ModerationCaseWriter {
    private final ModerationCaseRepository cases; private final UuidV7Generator ids; private final AppClock clock; private final OutboxEventWriter outbox;
    public JpaModerationCaseWriter(ModerationCaseRepository cases, UuidV7Generator ids, AppClock clock, OutboxEventWriter outbox) { this.cases=cases;this.ids=ids;this.clock=clock;this.outbox=outbox; }
    @Override @Transactional(propagation = Propagation.MANDATORY) public UUID openPreApproval(ModerationTargetRef target, String reasonCode) {
        var existing = switch(target.type()) { case CATALOG_ITEM -> cases.findByTargetCatalogItemIdAndCaseTypeAndCaseStatus(target.id(), ModerationCaseType.PRE_APPROVAL, ModerationCaseStatus.OPEN); case NEED_REQUEST -> cases.findByTargetNeedRequestIdAndCaseTypeAndCaseStatus(target.id(), ModerationCaseType.PRE_APPROVAL, ModerationCaseStatus.OPEN); default -> throw new IllegalArgumentException("Pre-approval is only supported for catalog items and need requests."); };
        if(existing.isPresent()) return existing.get().getId();
        UUID id=ids.generate(); ModerationCaseEntity opened=target.type()==ModerationTargetType.CATALOG_ITEM?ModerationCaseEntity.preApprovalCatalog(id,target.id(),clock.now()):ModerationCaseEntity.preApprovalNeed(id,target.id(),clock.now()); cases.save(opened); outbox.append(new OutboxEventIntent("MODERATION_CASE_OPENED","MODERATION_CASE",id,Map.of("targetType",target.type().name(),"targetId",target.id().toString()))); return id;
    }
}
