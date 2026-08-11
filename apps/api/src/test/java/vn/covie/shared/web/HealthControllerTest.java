package vn.covie.shared.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class HealthControllerTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsHealthAndGeneratedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("req_[A-Za-z0-9-]+")))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void preservesSafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "client-request-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "client-request-12345"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "bad value"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("req_[A-Za-z0-9-]+")));
    }
}
