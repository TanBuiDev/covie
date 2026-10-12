package vn.covie.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.shared.web.GlobalExceptionHandler;

class RedisRateLimitOutageHttpTest {
    @Test
    void actualConnectionLossFailsClosedOrOpenAndPublishesBoundedMetrics() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)) {
            redis.start();
            var configuration = new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
            var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500))
                    .shutdownTimeout(Duration.ofMillis(100)).build();
            var connections = new LettuceConnectionFactory(configuration, client);
            connections.afterPropertiesSet();
            connections.start();
            var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try {
                var properties = new RateLimitProperties(true, "test-only-outage", Map.of(
                        RateLimitGroup.AUTH_LOGIN_IP, new RateLimitPolicy(1, 1, Duration.ofHours(1), true),
                        RateLimitGroup.PUBLIC_DISCOVERY, new RateLimitPolicy(1, 1, Duration.ofHours(1), false)));
                var limiter = new RedisTokenBucketRateLimiter(new StringRedisTemplate(connections), properties);
                limiter.configureMetrics(new vn.covie.ops.OperationalMetrics(registry));
                var interceptor = new RateLimitInterceptor(limiter, new RateLimitFingerprintService(properties),
                        new ClientIpResolver());
                var mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).addInterceptors(interceptor)
                        .setControllerAdvice(new GlobalExceptionHandler(new vn.covie.ops.ApiErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()))).build();
                mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
                mvc.perform(post("/api/v1/auth/login")).andExpect(status().isTooManyRequests());
                assertThat(registry.get("covie.ratelimit.requests").tags("group", "AUTH_LOGIN_IP", "outcome", "denied")
                        .counter().count()).isEqualTo(1);
                // Stop only this test's container after establishing a working connection.
                redis.stop();
                mvc.perform(post("/api/v1/auth/login")).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.error.code").value("RATE_LIMITER_UNAVAILABLE"));
                mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
                assertThat(registry.get("covie.ratelimit.requests")
                        .tags("group", "PUBLIC_DISCOVERY", "outcome", "degraded_fail_open").counter().count()).isEqualTo(1);
                registry.getMeters().stream().filter(meter -> meter.getId().getName().equals("covie.ratelimit.requests"))
                        .forEach(meter -> assertThat(meter.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                                .containsExactlyInAnyOrder("group", "outcome"));
            } finally {
                registry.close();
                connections.destroy();
            }
        }
    }

    @RestController
    static class FixtureController {
        @PostMapping("/api/v1/auth/login") String login() { return "fixture"; }
        @GetMapping("/api/v1/categories") String categories() { return "fixture"; }
    }
}

