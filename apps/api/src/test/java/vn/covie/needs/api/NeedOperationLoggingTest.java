package vn.covie.needs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.application.NeedCommandService;
import vn.covie.needs.application.NeedEligibleItemReadService;
import vn.covie.needs.application.NeedMatchingService;
import vn.covie.needs.application.NeedReadService;
import vn.covie.needs.application.NeedResponseRateLimit;
import vn.covie.needs.application.NeedResponseReadService;
import vn.covie.needs.application.NeedResponseService;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.GlobalExceptionHandler;
import vn.covie.shared.web.RequestIdFilter;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NeedOperationLoggingTest.Config.class)
@WebAppConfiguration
class NeedOperationLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("vn.covie.needs.api.NeedOperationLogging");
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
    private final UUID userId = UUID.randomUUID();
    private final UUID needId = UUID.randomUUID();
    private final UUID responseId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    @org.springframework.beans.factory.annotation.Autowired WebApplicationContext context;
    @org.springframework.beans.factory.annotation.Autowired NeedResponseService responses;
    @org.springframework.beans.factory.annotation.Autowired NeedCommandService commands;
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        org.mockito.Mockito.reset(responses, commands);
        captured.start();
        logger.addAppender(captured);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        Jwt jwt = Jwt.withTokenValue("private-jwt-sentinel").header("alg", "RS256")
                .subject(userId.toString()).claim("sessionId", UUID.randomUUID().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(captured);
        captured.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsFinalSellerOutcomeWithOnlyWhitelistedIdentifiers() throws Exception {
        NeedResponseResponse body = new NeedResponseResponse(responseId, needId, merchantId,
                "private-merchant-name", locationId, "private-location-name", 123L,
                NeedResponseStatus.ACTIVE, "private-note-sentinel", 0, Instant.now(), List.of());
        when(responses.create(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(captured.list).isEmpty();
            return new IdempotencyExecutor.Execution<>(body, false);
        });
        mvc.perform(post("/api/v1/need-requests/{needId}/responses", needId)
                .header("X-Request-Id", "need-request-123")
                .header("Cookie", "private-cookie-sentinel")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"merchantLocationId":"%s","locationVariantIds":["%s"],"note":"private-note-sentinel"}
                        """.formatted(locationId, UUID.randomUUID())))
                .andExpect(status().isCreated());
        assertThat(captured.list).hasSize(1);
        String event = captured.list.getFirst().getFormattedMessage();
        assertThat(event).contains("requestId=need-request-123", "userId=" + userId,
                "merchantId=" + merchantId, "merchantLocationId=" + locationId,
                "needRequestId=" + needId, "responseId=" + responseId,
                "operation=NeedResponseController.create", "result=HTTP_SUCCESS", "errorCode=null");
        assertThat(event).doesNotContain("private-", "123L", "locationVariantIds");
    }

    @Test
    void logsExistingConflictCodeWithoutExceptionMessageOrPrivateRequest() throws Exception {
        ApiException failure = new ApiException(ErrorCode.NEED_RESPONSE_STATE_CONFLICT,
                HttpStatus.CONFLICT, "private-error-sentinel");
        when(responses.replace(any(), any(), any(), any())).thenThrow(failure);
        mvc.perform(put("/api/v1/need-requests/{needId}/responses/{responseId}", needId, responseId)
                .header("X-Request-Id", "need-request-456").header("If-Match", "\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"locationVariantIds":["%s"],"note":"private-note-sentinel"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
        assertThat(captured.list).hasSize(1);
        assertThat(captured.list.getFirst().getFormattedMessage()).contains(
                "result=HTTP_ERROR", "errorCode=NEED_RESPONSE_STATE_CONFLICT",
                "needRequestId=" + needId, "responseId=" + responseId)
                .doesNotContain("private-");
    }

    @Test
    void doesNotReportSuccessWhenServiceTransactionFails() throws Exception {
        when(responses.create(any(), any(), any())).thenThrow(
                new org.springframework.transaction.TransactionSystemException("private-transaction-sentinel"));
        mvc.perform(post("/api/v1/need-requests/{needId}/responses", needId)
                .header("X-Request-Id", "need-request-789")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"merchantLocationId":"%s","locationVariantIds":["%s"]}
                        """.formatted(locationId, UUID.randomUUID())))
                .andExpect(status().isInternalServerError());
        assertThat(captured.list).hasSize(1);
        assertThat(captured.list.getFirst().getFormattedMessage()).contains(
                "result=HTTP_ERROR", "errorCode=INTERNAL_ERROR", "merchantLocationId=" + locationId)
                .doesNotContain("HTTP_SUCCESS", "private-");
    }

    @Test
    void excludesBuyerCoordinatesAddressAndFreeTextFromOperationLog() throws Exception {
        var body = new vn.covie.needs.api.dto.NeedDetailResponse(needId,
                vn.covie.needs.model.NeedType.PRODUCT, vn.covie.needs.model.NeedStatus.ACTIVE,
                UUID.randomUUID(), "private-title", "private-query", "private-description", 1L,
                new vn.covie.needs.api.dto.NeedLocationSummaryResponse(UUID.randomUUID(), null, "UTC"),
                1000, Instant.now().plusSeconds(3600), 0L, null, java.util.Set.of(), Instant.now(), null);
        when(commands.create(any(), any())).thenReturn(new IdempotencyExecutor.Execution<>(body, false));
        mvc.perform(post("/api/v1/me/need-requests")
                .header("X-Request-Id", "need-buyer-request")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"PRODUCT","categoryId":"%s","title":"private-title",
                        "queryText":"private-query","description":"private-description",
                        "addressId":"%s","lat":10.123456,"lng":106.654321,
                        "radiusMeters":1000,"expiresAt":"%s"}
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600))))
                .andExpect(status().isCreated());
        assertThat(captured.list).hasSize(1);
        assertThat(captured.list.getFirst().getFormattedMessage())
                .contains("userId=" + userId, "needRequestId=" + needId,
                        "operation=MeNeedController.create", "result=HTTP_SUCCESS")
                .doesNotContain("private-", "10.123456", "106.654321", "addressId", "queryText");
    }

    @Configuration
    @EnableWebMvc
    @ComponentScan(basePackageClasses = MeNeedController.class)
    static class Config {
        @Bean vn.covie.billing.observability.BillingOperationMetrics billingMetrics() {
            return mock(vn.covie.billing.observability.BillingOperationMetrics.class);
        }
        @Bean vn.covie.billing.application.BillingEntitlementService billing() {
            return mock(vn.covie.billing.application.BillingEntitlementService.class);
        }
        @Bean NeedCommandService commands() { return mock(NeedCommandService.class); }
        @Bean NeedReadService reads() { return mock(NeedReadService.class); }
        @Bean NeedResponseReadService responseReads() { return mock(NeedResponseReadService.class); }
        @Bean NeedResponseService responses() { return mock(NeedResponseService.class); }
        @Bean NeedResponseRateLimit rateLimit() { return mock(NeedResponseRateLimit.class); }
        @Bean NeedMatchingService matching() { return mock(NeedMatchingService.class); }
        @Bean NeedEligibleItemReadService eligible() { return mock(NeedEligibleItemReadService.class); }
        @Bean GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler(mock(vn.covie.ops.ApiErrorMetrics.class));
        }
    }
}
