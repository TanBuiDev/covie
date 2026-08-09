package vn.covie.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void serializesStandardErrorEnvelope() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
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
    }
}
