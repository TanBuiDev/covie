package vn.covie.realtime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;

class MeEventStreamControllerTest {
    @Test
    void actualRegistryDeliversSignalsOnlyToTheTargetUsersStream() throws Exception {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        UUID firstUser = UUID.randomUUID(), secondUser = UUID.randomUUID();
        when(actors.currentActor()).thenReturn(new CurrentActor(firstUser, java.util.Set.of(), UUID.randomUUID()),
                new CurrentActor(secondUser, java.util.Set.of(), UUID.randomUUID()));
        SseConnectionRegistry registry = new SseConnectionRegistry(1000L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MeEventStreamController(actors, registry)).build();
        var first = mvc.perform(get("/api/v1/me/events")).andExpect(request().asyncStarted()).andReturn();
        var second = mvc.perform(get("/api/v1/me/events")).andExpect(request().asyncStarted()).andReturn();
        registry.publish(new SseSignal(firstUser, "order.updated", java.util.Map.of("orderId", "only-first-user")));
        org.assertj.core.api.Assertions.assertThat(first.getResponse().getContentAsString()).contains("only-first-user");
        org.assertj.core.api.Assertions.assertThat(second.getResponse().getContentAsString()).contains("sync-required").doesNotContain("only-first-user");
        registry.publish(new SseSignal(secondUser, "notification.created", java.util.Map.of("notificationId", "only-second-user")));
        org.assertj.core.api.Assertions.assertThat(second.getResponse().getContentAsString()).contains("only-second-user");
        org.assertj.core.api.Assertions.assertThat(first.getResponse().getContentAsString()).doesNotContain("only-second-user");
        first.getRequest().getAsyncContext().complete();
        second.getRequest().getAsyncContext().complete();
    }

    @Test
    void streamIsPrivateNoStoreAndBoundToAuthenticatedUser() throws Exception {
        CurrentActorProvider actors=mock(CurrentActorProvider.class); SseConnectionRegistry registry=mock(SseConnectionRegistry.class);
        UUID user=UUID.randomUUID(); when(actors.currentActor()).thenReturn(new CurrentActor(user, java.util.Set.of(), UUID.randomUUID())); when(registry.register(user)).thenReturn(new SseEmitter(1000L));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new MeEventStreamController(actors,registry)).build();
        mvc.perform(get("/api/v1/me/events")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")));
        verify(registry).register(user);
    }
}
