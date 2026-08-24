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
    void streamIsPrivateNoStoreAndBoundToAuthenticatedUser() throws Exception {
        CurrentActorProvider actors=mock(CurrentActorProvider.class); SseConnectionRegistry registry=mock(SseConnectionRegistry.class);
        UUID user=UUID.randomUUID(); when(actors.currentActor()).thenReturn(new CurrentActor(user, java.util.Set.of(), UUID.randomUUID())); when(registry.register(user)).thenReturn(new SseEmitter(1000L));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new MeEventStreamController(actors,registry)).build();
        mvc.perform(get("/api/v1/me/events")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")));
        verify(registry).register(user);
    }
}
