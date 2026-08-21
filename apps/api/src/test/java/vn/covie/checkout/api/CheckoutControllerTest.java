package vn.covie.checkout.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.checkout.api.dto.CheckoutPreviewResponse;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.checkout.model.FulfillmentMethod;

class CheckoutControllerTest {
    @Test
    void previewResponseIsPrivateAndNeverStored() throws Exception {
        CheckoutPreviewService previews = mock(CheckoutPreviewService.class);
        when(previews.preview(any())).thenReturn(new CheckoutPreviewResponse(
                "chk_token", Instant.parse("2026-08-22T00:15:00Z"), UUID.randomUUID(),
                50_000, 0L, 50_000L, 50_000, "VND", FulfillmentMethod.PICKUP));
        var mvc = MockMvcBuilders.standaloneSetup(new CheckoutController(previews)).build();

        mvc.perform(post("/api/v1/checkouts/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"DIRECT\",\"items\":[{\"locationVariantId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":1}],\"fulfillmentMethod\":\"PICKUP\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"));
    }
}
