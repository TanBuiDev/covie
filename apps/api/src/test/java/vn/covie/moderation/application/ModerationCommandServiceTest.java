package vn.covie.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.moderation.api.dto.ModerationActionRequest;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationCaseEntity;
import vn.covie.moderation.model.ModerationCaseStatus;
import vn.covie.moderation.persistence.ModerationActionRepository;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;

class ModerationCommandServiceTest {
    @Test
    void approvalResolvesOpenPreapprovalAndAppliesTargetOnce() {
        UUID caseId = UUID.randomUUID(); UUID targetId = UUID.randomUUID(); UUID adminId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        ModerationCaseEntity moderationCase = ModerationCaseEntity.preApprovalCatalog(caseId, targetId, now.minusSeconds(60));
        ModerationCaseRepository cases = Mockito.mock(ModerationCaseRepository.class);
        ModerationTargetHandler handler = Mockito.mock(ModerationTargetHandler.class);
        when(handler.supports()).thenReturn(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM);
        ModerationTargetHandlerRegistry handlers = new ModerationTargetHandlerRegistry(java.util.List.of(handler));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(moderationCase));
        AdminAccessPolicy access = Mockito.mock(AdminAccessPolicy.class);
        when(access.requireAdmin()).thenReturn(new CurrentActor(adminId, java.util.Set.of(), UUID.randomUUID()));
        when(handler.apply(any(), any(), any())).thenReturn(null);
        ModerationCommandService service = new ModerationCommandService(access, cases, Mockito.mock(ModerationActionRepository.class),
                handlers, Mockito.mock(vn.covie.audit.AuditLogWriter.class), Mockito.mock(vn.covie.shared.outbox.OutboxEventWriter.class),
                new UuidV7Generator(), () -> now, Mockito.mock(vn.covie.shared.web.RequestIdAccessor.class));

        var result = service.executeWithoutIdempotency(caseId, ModerationActionType.APPROVE, new ModerationActionRequest(null, null));

        assertThat(result.status()).isEqualTo(ModerationCaseStatus.RESOLVED);
        verify(handler).apply(targetId, ModerationActionType.APPROVE, now);
    }

    @Test
    void secondConflictingActionSeesResolvedCaseAndDoesNotMutateTargetAgain() {
        UUID caseId = UUID.randomUUID(); UUID targetId = UUID.randomUUID(); Instant now = Instant.parse("2026-08-24T00:00:00Z");
        ModerationCaseEntity moderationCase = ModerationCaseEntity.preApprovalCatalog(caseId, targetId, now);
        ModerationCaseRepository cases = Mockito.mock(ModerationCaseRepository.class); when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(moderationCase));
        ModerationTargetHandler handler = Mockito.mock(ModerationTargetHandler.class); when(handler.supports()).thenReturn(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM);
        AdminAccessPolicy access = Mockito.mock(AdminAccessPolicy.class); when(access.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(), java.util.Set.of(), UUID.randomUUID()));
        ModerationCommandService service = new ModerationCommandService(access,cases,Mockito.mock(ModerationActionRepository.class),new ModerationTargetHandlerRegistry(java.util.List.of(handler)),Mockito.mock(vn.covie.audit.AuditLogWriter.class),Mockito.mock(vn.covie.shared.outbox.OutboxEventWriter.class),new UuidV7Generator(),()->now,Mockito.mock(vn.covie.shared.web.RequestIdAccessor.class));
        service.executeWithoutIdempotency(caseId,ModerationActionType.APPROVE,new ModerationActionRequest(null,null));
        assertThatThrownBy(()->service.executeWithoutIdempotency(caseId,ModerationActionType.REJECT,new ModerationActionRequest("policy",null))).isInstanceOf(vn.covie.shared.web.ApiException.class).extracting(error->((vn.covie.shared.web.ApiException)error).code()).isEqualTo(vn.covie.shared.web.ErrorCode.MODERATION_CASE_STATE_CONFLICT);
        verify(handler,times(1)).apply(targetId,ModerationActionType.APPROVE,now);
    }
}
