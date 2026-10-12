package vn.covie.settings.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.application.SystemSettingService;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.GlobalExceptionHandler;

class AdminSystemSettingControllerJsonBindingTest {

    @Test
    void bindsBooleanSettingValueBeforeInvokingTheService() throws Exception {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.patch(eq("review.enabled"), isNull(), any(PatchSystemSettingRequest.class)))
                .thenThrow(new ApiException(
                        ErrorCode.SYSTEM_SETTING_VALUE_INVALID,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "service reached"));

        var mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSystemSettingController(service))
                .setControllerAdvice(new GlobalExceptionHandler(new vn.covie.ops.ApiErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())))
                .build();

        mockMvc.perform(patch("/api/v1/admin/settings/review.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopeType":"GLOBAL","value":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.message").value("service reached"));
    }
}

