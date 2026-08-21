package vn.covie.shipping.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.shipping.api.dto.ShippingPolicyResponse;
import vn.covie.shipping.application.ShippingPolicyService;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyStatus;

class MerchantShippingPolicyControllerIntegrationTest {
    @Test
    void createsAndUpdatesPolicyWithVersionEtags() throws Exception {
        UUID merchant = UUID.randomUUID(), location = UUID.randomUUID(), policy = UUID.randomUUID();
        ShippingPolicyService service = mock(ShippingPolicyService.class);
        when(service.create(eq(merchant), eq(location), any())).thenReturn(response(policy, location, 0));
        when(service.update(eq(merchant), eq(location), eq(policy), eq("\"v0\""), any())).thenReturn(response(policy, location, 1));
        var mvc = MockMvcBuilders.standaloneSetup(new MerchantShippingPolicyController(service)).build();
        String body = "{\"mode\":\"FIXED\",\"baseFee\":20000,\"currencyCode\":\"VND\",\"status\":\"ACTIVE\",\"rules\":[]}";
        String path = "/api/v1/merchants/" + merchant + "/locations/" + location + "/shipping-policies";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.id").value(policy.toString()));
        mvc.perform(patch(path + "/" + policy).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"v1\""));
    }
    private static ShippingPolicyResponse response(UUID id, UUID location, long version) {
        return new ShippingPolicyResponse(id, location, ShippingMode.FIXED, 20_000L, "VND", null, ShippingPolicyStatus.ACTIVE, version, List.of());
    }
}
