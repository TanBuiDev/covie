package vn.covie.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
class GlobalExceptionHandlerTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meters;

    @Test
    void recordsMappedApiErrorWithoutChangingItsEnvelope() throws Exception {
        var counter = meters.find("covie.api.errors").tags("code", "RESOURCE_NOT_FOUND", "status", "404").counter();
        double before = counter == null ? 0 : counter.count();
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        var recorded = meters.get("covie.api.errors").tags("code", "RESOURCE_NOT_FOUND", "status", "404").counter();
        org.assertj.core.api.Assertions.assertThat(recorded.count()).isEqualTo(before + 1);
        org.assertj.core.api.Assertions.assertThat(recorded.getId().getTags()).hasSize(2);
    }

    @Test
    void serializesStandardErrorEnvelope() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void serializesOptimisticLockAsTheSharedVersionConflictContract() throws Exception {
        mockMvc.perform(get("/__test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test void unmappedRequestsUseTheNotFoundEnvelopeInsteadOfAnInternalError() throws Exception {
        mockMvc.perform(get("/__test/unmapped-route"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test void unsupportedMethodsPreserveTheFrameworkStatusAndAllowedMethods() throws Exception {
        mockMvc.perform(post("/__test/not-found").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow",org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @RestController
    static class ErrorProbeController {
        @GetMapping("/__test/not-found")
        void notFound() {
            throw new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Resource not found.");
        }

        @GetMapping("/__test/optimistic-lock")
        void optimisticLock() { throw new OptimisticLockException("stale offer"); }
    }
}
