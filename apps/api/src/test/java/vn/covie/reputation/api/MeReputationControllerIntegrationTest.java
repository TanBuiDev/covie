package vn.covie.reputation.api;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.reputation.model.UserReputationStatsEntity;
import vn.covie.reputation.persistence.UserReputationStatsRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MeReputationControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired UserReputationStatsRepository reputation;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/reputation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.eligibleOrders").doesNotExist());
    }

    @Test
    void returnsOnlyTheAuthenticatedBuyersProjectionWithoutCachingIt() throws Exception {
        Actor buyer = actor("buyer");
        Actor other = actor("other");
        stats(buyer.userId(), 5, 4);
        stats(other.userId(), 10, 10);

        mockMvc.perform(get("/api/v1/me/reputation").with(buyer.jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(containsString("private"), containsString("no-store"))))
                .andExpect(jsonPath("$.eligibleOrders").value(5))
                .andExpect(jsonPath("$.completedOrders").value(4))
                .andExpect(jsonPath("$.completionRateBasisPoints").value(8000))
                .andExpect(jsonPath("$.warning").value(false));
    }

    @Test
    void returnsNullRateForABuyerWithoutEligibleOrders() throws Exception {
        Actor buyer = actor("new-buyer");

        mockMvc.perform(get("/api/v1/me/reputation").with(buyer.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleOrders").value(0))
                .andExpect(jsonPath("$.completedOrders").value(0))
                .andExpect(jsonPath("$.completionRateBasisPoints").doesNotExist())
                .andExpect(jsonPath("$.warning").value(false));
    }

    private Actor actor(String prefix) {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId, prefix + "-" + userId, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plusSeconds(86_400)));
        return new Actor(userId, sessionId);
    }

    private void stats(UUID userId, long eligible, long completed) {
        UserReputationStatsEntity stats = UserReputationStatsEntity.empty(userId, Instant.now());
        stats.adjust(eligible, completed, 0, 0, 0, Instant.now());
        reputation.saveAndFlush(stats);
    }

    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                            .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
