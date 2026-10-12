package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.realtime.*;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class SettingDispatchAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired vn.covie.outbox.application.OutboxProcessor processor;
    @Autowired vn.covie.settings.application.SystemSettingResolver resolver;
    @MockitoSpyBean SseSignalPublisher publisher;

    @Test void committedSettingChangeResolvesImmediatelyAndSignalsOnlyAdmins() throws Exception {
        jdbc.execute("truncate table outbox_events cascade");
        var admin = activeUser("Setting author");
        var otherAdmin = activeUser("Setting observer");
        activeUser("Ordinary observer");
        for (var user : List.of(admin, otherAdmin)) roles.saveAndFlush(SystemRoleAssignmentEntity.assign(
                UUID.randomUUID(), user.getId(), SystemRole.ADMIN, Instant.now()));
        String bearer = "Bearer " + issuer.issue(admin.getId(), device()).accessToken().value();
        mvc.perform(patch("/api/v1/admin/settings/bulk.max_items").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scopeType\":\"GLOBAL\",\"value\":120}"))
                .andExpect(status().isCreated());
        UUID event = jdbc.queryForObject("select id from outbox_events where event_type='SYSTEM_SETTING_CHANGED'", UUID.class);
        assertThat(resolver.integer("bulk.max_items", vn.covie.settings.application.SystemSettingContext.globalOnly())).isEqualTo(120);
        var sent = new ArrayList<SseSignal>();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event)).isEqualTo("PUBLISHED");
            Collection<SseSignal> signals = invocation.getArgument(0);
            sent.addAll(signals);
            return invocation.callRealMethod();
        }).when(publisher).publish(org.mockito.ArgumentMatchers.anyCollection());
        assertThat(processor.processOne()).isTrue();
        assertThat(sent).extracting(SseSignal::userId).containsExactlyInAnyOrder(admin.getId(), otherAdmin.getId());
        assertThat(sent).allSatisfy(signal -> {
            assertThat(signal.eventName()).isEqualTo("admin.setting.updated");
            assertThat(signal.data()).containsExactlyEntriesOf(Map.of("settingKey", "bulk.max_items"));
        });
        assertThat(processor.processOne()).isFalse();
        assertThat(sent).hasSize(2);
    }
}
