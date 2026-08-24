package vn.covie.admin.api;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.AdminOrderSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.application.AdminReadService;
import vn.covie.orders.api.dto.OrderDetailResponse;

@RestController @RequestMapping("/api/v1/admin/orders")
public final class AdminOrderController {
    private final AdminReadService reads; public AdminOrderController(AdminReadService reads){this.reads=reads;}
    @GetMapping public ResponseEntity<AdminPageResponse<AdminOrderSummaryResponse>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String type,@RequestParam(required=false)UUID merchantId,@RequestParam(required=false)UUID buyerUserId,@RequestParam(required=false)String orderCode,@RequestParam(required=false)String sort){return noStore(reads.orders(page,size,status,type,merchantId,buyerUserId,orderCode,sort));}
    @GetMapping("/{orderId}") public ResponseEntity<OrderDetailResponse> detail(@PathVariable UUID orderId){return noStore(reads.order(orderId));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);}
}
