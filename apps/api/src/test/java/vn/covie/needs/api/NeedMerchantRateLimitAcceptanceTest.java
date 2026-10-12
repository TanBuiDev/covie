package vn.covie.needs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.*;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NeedMerchantRateLimitAcceptanceTest extends PostgresIntegrationTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);
    static { REDIS.start(); }
    @DynamicPropertySource
    static void rateProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        properties.add("covie.rate-limit.enabled", () -> true);
        properties.add("covie.rate-limit.hmac-key", () -> "test-only-merchant-rate-limit-key");
        properties.add("covie.rate-limit.policies.NEED_RESPONSE.capacity", () -> 1);
        properties.add("covie.rate-limit.policies.NEED_RESPONSE.refill-tokens", () -> 1);
        properties.add("covie.rate-limit.policies.NEED_RESPONSE.refill-period", () -> "1h");
        properties.add("covie.rate-limit.policies.NEED_RESPONSE.fail-closed", () -> true);
    }
    @AfterAll static void stopRedis() { REDIS.stop(); }
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
    @Autowired StringRedisTemplate redis;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void signedSellerUsesSeparateAuthorizedMerchantBucketsWithoutBusinessWrites() throws Exception {
        UUID seller = UUID.randomUUID(), outsider = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(seller, "Rate seller", Instant.now()));
        users.saveAndFlush(UserEntity.active(outsider, "Rate outsider", Instant.now()));
        var first = merchantLocation(seller);
        var second = merchantLocation(seller);
        var sharedLocation = locations.saveAndFlush(location(first.getMerchantProfileId()));
        String token = "Bearer " + issuer.issue(seller,
                new vn.covie.auth.model.DeviceMetadata("Rate test", "merchant-rate")).accessToken().value();
        String foreign = "Bearer " + issuer.issue(outsider,
                new vn.covie.auth.model.DeviceMetadata("Rate test", "merchant-rate")).accessToken().value();
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        String route = "/api/v1/need-requests/" + UUID.randomUUID() + "/responses";
        long before = jdbc.queryForObject("select count(*) from need_responses", Long.class);
        for (var location : java.util.List.of(first, second)) {
            String payload = payload(location.getId());
            mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID())
                    .contentType("application/json").content(payload)).andExpect(status().isNotFound());
            mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID())
                    .contentType("application/json").content(payload)).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(header().exists("Retry-After"));
        }
        mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID())
                .header("X-Merchant-Id", UUID.randomUUID()).contentType("application/json")
                .content(payload(sharedLocation.getId()))).andExpect(status().isTooManyRequests());
        mvc.perform(post(route).header("Authorization", foreign).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(payload(first.getId()))).andExpect(status().isNotFound());
        assertThat(redis.hasKey("covie:rl:NEED_RESPONSE:" + outsider + ":" + first.getMerchantProfileId())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from need_responses", Long.class)).isEqualTo(before);

        // Persisted fixtures exercise the other two command routes with real ownership checks.
        var category = web.getBean(vn.covie.catalog.persistence.CategoryRepository.class).saveAndFlush(
                vn.covie.catalog.model.CategoryEntity.create(UUID.randomUUID(), null, "Rate category",
                        "rate-" + UUID.randomUUID(), vn.covie.catalog.model.CategoryCatalogType.PRODUCT,
                        vn.covie.catalog.model.CategoryStatus.ACTIVE, 0));
        var point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        var need = web.getBean(vn.covie.needs.persistence.NeedRequestRepository.class).saveAndFlush(
                vn.covie.needs.model.NeedRequestEntity.create(UUID.randomUUID(), seller,
                        vn.covie.needs.model.NeedType.PRODUCT, category.getId(), "Rate need", "rate", null,
                        1L, null, point, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                        5000, Instant.now().plusSeconds(3600), Instant.now()));
        var responses = web.getBean(vn.covie.needs.persistence.NeedResponseRepository.class);
        for (boolean withdraw : java.util.List.of(false, true)) {
            var location = merchantLocation(seller);
            var response = responses.saveAndFlush(vn.covie.needs.model.NeedResponseEntity.active(
                    UUID.randomUUID(), need.getId(), location.getMerchantProfileId(), location.getId(),
                    seller, null, Instant.now()));
            String responseRoute = "/api/v1/need-requests/" + need.getId() + "/responses/" + response.getId();
            var request = withdraw ? post(responseRoute + "/withdraw")
                    : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(responseRoute)
                            .contentType("application/json").content(payload(location.getId()));
            request.header("Authorization", token);
            mvc.perform(request).andExpect(status().is(withdraw ? 400 : 428));
            mvc.perform(request).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
            assertThat(responses.findById(response.getId()).orElseThrow().getResponseStatus())
                    .isEqualTo(vn.covie.needs.model.NeedResponseStatus.ACTIVE);
        }
    }
    private MerchantLocationEntity merchantLocation(UUID user) {
        UUID id = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(id, "Rate merchant", "rate-" + id, Instant.now()));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), id, user, MerchantRole.OWNER, Instant.now()));
        return locations.saveAndFlush(location(id));
    }
    private MerchantLocationEntity location(UUID merchant) {
        var point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        return MerchantLocationEntity.create(UUID.randomUUID(), merchant, "Rate location", "1 CoVie", null,
                point, PinSource.GPS, "Asia/Ho_Chi_Minh", true, false, false, null, "VND", Instant.now());
    }
    private String payload(UUID location) {
        return "{\"merchantLocationId\":\"" + location + "\",\"locationVariantIds\":[\"" + UUID.randomUUID() + "\"]}";
    }
}
