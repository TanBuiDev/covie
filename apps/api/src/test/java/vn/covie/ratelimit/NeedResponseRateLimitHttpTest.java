package vn.covie.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.needs.api.NeedResponseController;
import vn.covie.needs.application.NeedResponseService;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.GlobalExceptionHandler;

class NeedResponseRateLimitHttpTest {
    @Test
    void eachMutationEnforcesRedisBucketAndReturnsRetryAfter() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)) {
            redis.start();
            var connections = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connections.afterPropertiesSet();
            connections.start();
            try {
                var properties = new RateLimitProperties(true, "test-only", Map.of(RateLimitGroup.NEED_RESPONSE,
                        new RateLimitPolicy(1, 1, Duration.ofHours(1), true)));
                var interceptor = new RateLimitInterceptor(
                        new RedisTokenBucketRateLimiter(new StringRedisTemplate(connections), properties),
                        new RateLimitFingerprintService(properties), new ClientIpResolver());
                // Business service is deliberately isolated; this verifies MVC mapping, Redis and HTTP errors.
                var service = mock(NeedResponseService.class);
                var actors = mock(vn.covie.auth.security.CurrentActorProvider.class);
                when(actors.currentActor()).thenAnswer(invocation -> new vn.covie.shared.security.CurrentActor(
                        java.util.UUID.nameUUIDFromBytes(SecurityContextHolder.getContext().getAuthentication()
                                .getName().getBytes(java.nio.charset.StandardCharsets.UTF_8)), java.util.Set.of(),
                        java.util.UUID.randomUUID()));
                var locations = mock(vn.covie.merchants.persistence.MerchantLocationRepository.class);
                when(locations.findById(any())).thenAnswer(invocation -> {
                    java.util.UUID id = invocation.getArgument(0);
                    var location = mock(vn.covie.merchants.model.MerchantLocationEntity.class);
                    when(location.getId()).thenReturn(id);
                    when(location.getMerchantProfileId()).thenReturn(id.toString().endsWith("000006")
                            ? java.util.UUID.fromString("00000000-0000-0000-0000-000000000003") : id);
                    return java.util.Optional.of(location);
                });
                var responses = mock(vn.covie.needs.persistence.NeedResponseRepository.class);
                var persistedResponse = mock(vn.covie.needs.model.NeedResponseEntity.class);
                var needId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
                var locationId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");
                when(persistedResponse.getNeedRequestId()).thenReturn(needId);
                when(persistedResponse.getMerchantProfileId()).thenReturn(locationId);
                when(persistedResponse.getMerchantLocationId()).thenReturn(locationId);
                when(responses.findById(any())).thenReturn(java.util.Optional.of(persistedResponse));
                var access = mock(vn.covie.merchants.security.MerchantAccessPolicy.class);
                var limiter = new RedisTokenBucketRateLimiter(new StringRedisTemplate(connections), properties);
                var scopedLimit = new vn.covie.needs.application.NeedResponseRateLimit(
                        limiter, actors, locations, responses, access);
                var unavailable = new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Fixture.");
                when(service.create(any(), any(), any())).thenThrow(unavailable);
                when(service.replace(any(), any(), any(), any())).thenThrow(unavailable);
                when(service.withdraw(any(), any(), any())).thenThrow(unavailable);
                var mvc = MockMvcBuilders.standaloneSetup(new NeedResponseController(service, scopedLimit))
                        .addInterceptors(interceptor).setControllerAdvice(new GlobalExceptionHandler(new vn.covie.ops.ApiErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()))).build();
                String base = "/api/v1/need-requests/00000000-0000-0000-0000-000000000001/responses";
                String response = "/00000000-0000-0000-0000-000000000002";
                String payload = "{\"merchantLocationId\":\"00000000-0000-0000-0000-000000000003\","
                        + "\"locationVariantIds\":[\"00000000-0000-0000-0000-000000000004\"]}";
                var requests = List.of(post(base), put(base + response), post(base + response + "/withdraw"));
                for (int index = 0; index < requests.size(); index++) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            "seller-" + index, "unused", List.of()));
                    var request = requests.get(index).contentType("application/json").content(payload);
                    mvc.perform(request).andExpect(status().isNotFound());
                    mvc.perform(request).andExpect(status().isTooManyRequests())
                            .andExpect(jsonPath("error.code").value("RATE_LIMIT_EXCEEDED"))
                            .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "multi-merchant-seller", "unused", List.of()));
                mvc.perform(post(base).contentType("application/json").content(payload))
                        .andExpect(status().isNotFound());
                mvc.perform(post(base).contentType("application/json").content(payload.replace(
                        "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000005")))
                        .andExpect(status().isNotFound());
                // A second location owned by the same merchant shares its bucket; client headers cannot change it.
                mvc.perform(post(base).contentType("application/json").content(payload.replace(
                        "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000006"))
                        .header("X-Merchant-Id", java.util.UUID.randomUUID().toString()))
                        .andExpect(status().isTooManyRequests());
                var deniedId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000007");
                doThrow(unavailable).when(access).requireLocation(eq(deniedId), eq(deniedId), any());
                mvc.perform(post(base).contentType("application/json").content(payload.replace(
                        "00000000-0000-0000-0000-000000000003", deniedId.toString())))
                        .andExpect(status().isNotFound());
                org.assertj.core.api.Assertions.assertThat(new StringRedisTemplate(connections).hasKey(
                        "covie:rl:NEED_RESPONSE:" + actors.currentActor().userId() + ":" + deniedId)).isFalse();
                verify(service, times(3)).create(any(), any(), any());
                verify(service, times(1)).replace(any(), any(), any(), any());
                verify(service, times(1)).withdraw(any(), any(), any());
            } finally {
                SecurityContextHolder.clearContext();
                connections.destroy();
            }
        }
    }
}

