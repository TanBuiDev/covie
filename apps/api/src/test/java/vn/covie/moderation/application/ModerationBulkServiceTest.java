package vn.covie.moderation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.moderation.api.dto.BulkModerationRequest;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class ModerationBulkServiceTest {
    @Test
    void rejectsUnsafeBulkActionsBeforeInvokingAnyItem() {
        AdminAccessPolicy access = Mockito.mock(AdminAccessPolicy.class);
        when(access.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(), java.util.Set.of(), UUID.randomUUID()));
        ModerationBulkItemExecutor items = Mockito.mock(ModerationBulkItemExecutor.class);
        ModerationBulkService service = new ModerationBulkService(access, Mockito.mock(SystemSettingResolver.class), items);

        assertThatThrownBy(() -> service.execute("batch-key", new BulkModerationRequest(
                ModerationActionType.APPROVE, List.of(UUID.randomUUID()), "policy", null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.MODERATION_BULK_ACTION_NOT_SUPPORTED);

        verify(items, never()).execute(any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateCaseIdsBeforeInvokingAnyItem() {
        UUID caseId = UUID.randomUUID();
        AdminAccessPolicy access = Mockito.mock(AdminAccessPolicy.class);
        when(access.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(), java.util.Set.of(), UUID.randomUUID()));
        SystemSettingResolver settings = Mockito.mock(SystemSettingResolver.class);
        when(settings.integer(org.mockito.ArgumentMatchers.eq("bulk.max_items"), any())).thenReturn(100);
        ModerationBulkItemExecutor items = Mockito.mock(ModerationBulkItemExecutor.class);
        ModerationBulkService service = new ModerationBulkService(access, settings, items);

        assertThatThrownBy(() -> service.execute("batch-key", new BulkModerationRequest(
                ModerationActionType.HIDE, List.of(caseId, caseId), "policy", null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(items, never()).execute(any(), any(), any(), any());
    }
}
