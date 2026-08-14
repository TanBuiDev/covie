package vn.covie.media.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MediaControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @MockitoBean ObjectStorage storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        when(storage.presignPut(any(), any(), anyLong(), any())).thenReturn(new PresignedUpload(
                URI.create("https://uploads.example/signed"), "PUT", Instant.now().plusSeconds(600), Map.of("content-type", "image/png")));
    }

    @Test
    void createsDirectUploadOnlyForAuthenticatedActorAndDoesNotExposeStorageKey() throws Exception {
        Actor actor = actor();
        mockMvc.perform(post("/api/v1/media/uploads").with(actor.jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"USER_AVATAR\",\"contentType\":\"image/png\",\"fileSize\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.uploadUrl").value("https://uploads.example/signed"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    private Actor actor() {
        UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Media API", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }
    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token
                    .subject(userId.toString()).claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
