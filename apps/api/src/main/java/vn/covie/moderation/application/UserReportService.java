package vn.covie.moderation.application;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.moderation.api.dto.CreateReportRequest;
import vn.covie.moderation.api.dto.ReportCreatedResponse;
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

@Service
public class UserReportService {
    private final CurrentActorProvider actors; private final ReportTargetVisibilityPolicy visibility; private final ModerationCaseRepository cases; private final UuidV7Generator ids; private final OutboxEventWriter outbox; private final AppClock clock;
    public UserReportService(CurrentActorProvider actors,ReportTargetVisibilityPolicy visibility,ModerationCaseRepository cases,UuidV7Generator ids,OutboxEventWriter outbox,AppClock clock){this.actors=actors;this.visibility=visibility;this.cases=cases;this.ids=ids;this.outbox=outbox;this.clock=clock;}
    @Transactional public ReportCreatedResponse create(CreateReportRequest request) {
        if(request.targetType()==ModerationTargetType.USER) throw new ApiException(ErrorCode.REPORT_TARGET_TYPE_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,"User reports are not supported.");
        var actor=actors.currentActor(); if(!visibility.isVisible(actor.userId(),request.targetType(),request.targetId(),request.contextMerchantId())) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");
        var created=ModerationCaseEntity.userReport(ids.generate(),actor.userId(),new ModerationTargetRef(request.targetType(),request.targetId()),request.reasonCode(),request.note(),clock.now()); cases.save(created); outbox.append(new OutboxEventIntent("MODERATION_CASE_OPENED","MODERATION_CASE",created.getId(),Map.of("targetType",request.targetType().name(),"targetId",request.targetId().toString()))); return new ReportCreatedResponse(created.getId(),created.getCaseStatus());
    }
}
