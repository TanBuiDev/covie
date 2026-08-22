package vn.covie.orders.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.orders.api.dto.OrderPageResponse;
import vn.covie.orders.application.OrderReadService;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/orders")
public class MerchantOrderController {
    private final OrderReadService reads;

    public MerchantOrderController(OrderReadService reads) { this.reads = reads; }

    @GetMapping
    public OrderPageResponse list(@PathVariable UUID merchantId) { return reads.merchantOrders(merchantId); }
}
