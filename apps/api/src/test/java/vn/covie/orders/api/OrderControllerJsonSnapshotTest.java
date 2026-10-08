package vn.covie.orders.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.orders.api.dto.OrderDetailResponse;
import vn.covie.orders.api.dto.OrderFulfillmentResponse;
import vn.covie.orders.api.dto.OrderItemResponse;
import vn.covie.orders.application.OrderCancellationService;
import vn.covie.orders.application.OrderCommandIdempotencyService;
import vn.covie.orders.application.OrderCommandService;
import vn.covie.orders.application.OrderCompletionService;
import vn.covie.orders.application.OrderFulfillmentService;
import vn.covie.orders.application.OrderIssueService;
import vn.covie.orders.application.OrderNoShowService;
import vn.covie.orders.application.OrderReadService;
import vn.covie.orders.model.OrderSourceType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.shared.commerce.PaymentMethod;

class OrderControllerJsonSnapshotTest {

    @Test
    void serializesStoredJsonSnapshotsAsTheirJsonContent() throws Exception {
        UUID orderId = UUID.randomUUID();
        var item = new OrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CatalogItemType.PRODUCT, 1, 85_000, 85_000, "VND",
                Map.of("name", "Rau củ theo mùa"), null);
        var fulfillment = new OrderFulfillmentResponse(ProductFulfillmentMethod.PICKUP, UUID.randomUUID(), null, null,
                Map.of("name", "Chợ Xanh"), null, null,
                Map.of("recipientName", "Minh"),
                Map.of("phone", "0909"), null,
                null, null, null, null, null, null, null);
        var response = new OrderDetailResponse(orderId, "CV1024", OrderType.PRODUCT, OrderStatus.COMPLETED, 1,
                OrderSourceType.MARKETPLACE, null, null, List.of(item), null, fulfillment, PaymentMethod.COD,
                null, null, List.of(), Set.of(), Instant.now(), null, Instant.now(), null, null);

        OrderReadService reads = mock(OrderReadService.class);
        when(reads.detail(orderId)).thenReturn(response);
        var controller = new OrderController(reads, mock(OrderCommandService.class), mock(OrderCancellationService.class),
                mock(OrderFulfillmentService.class), mock(OrderCompletionService.class), mock(OrderIssueService.class),
                mock(OrderNoShowService.class), mock(OrderCommandIdempotencyService.class));
        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snapshot.name").value("Rau củ theo mùa"))
                .andExpect(jsonPath("$.items[0].snapshot.object").doesNotExist())
                .andExpect(jsonPath("$.fulfillment.pickupLocation.name").value("Chợ Xanh"))
                .andExpect(jsonPath("$.fulfillment.buyerContact.recipientName").value("Minh"));
    }
}
