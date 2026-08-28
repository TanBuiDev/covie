package vn.covie.reputation.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
class MeReputationControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/reputation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCurrentBuyersPolicyBackedSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "reputation-buyer", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS)));
        mockMvc.perform(get("/api/v1/me/reputation").with(jwt().jwt(token -> token
                        .subject(userId.toString())
                        .claim("sessionId", sessionId.toString())
                        .claim("roles", List.of("USER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleOrders").value(0))
                .andExpect(jsonPath("$.completedOrders").value(0))
                .andExpect(jsonPath("$.completionRateBasisPoints").doesNotExist())
                .andExpect(jsonPath("$.warning").value(false));
    }
}
