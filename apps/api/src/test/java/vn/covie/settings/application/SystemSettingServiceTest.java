package vn.covie.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.persistence.SystemSettingHistoryRepository;
import vn.covie.settings.persistence.SystemSettingRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;

class SystemSettingServiceTest {
    @Test void createsGlobalOverrideWithoutIfMatch() {
        SystemSettingRepository settings = Mockito.mock(SystemSettingRepository.class);
        when(settings.findBySettingKeyAndScopeType("review.enabled", SettingScopeType.GLOBAL)).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CurrentActor actor = new CurrentActor(UUID.randomUUID(), Set.of(SystemRole.ADMIN), UUID.randomUUID());
        CurrentActorProvider actors = Mockito.mock(CurrentActorProvider.class); when(actors.currentActor()).thenReturn(actor);
        SystemSettingService service = new SystemSettingService(new AdminAccessPolicy(actors), new SystemSettingRegistry(), settings,
                Mockito.mock(SystemSettingHistoryRepository.class), Mockito.mock(vn.covie.audit.AuditLogWriter.class),
                Mockito.mock(vn.covie.shared.outbox.OutboxEventWriter.class), Mockito.mock(vn.covie.shared.web.RequestIdAccessor.class), new UuidV7Generator(), () -> java.time.Instant.parse("2026-08-24T00:00:00Z"), null, null, null);

        var result = service.patch("review.enabled", null, new PatchSystemSettingRequest(SettingScopeType.GLOBAL, null, JsonNodeFactory.instance.booleanNode(false)));

        assertThat(result.created()).isTrue();
        assertThat(result.setting().getValueJson().booleanValue()).isFalse();
    }
}
