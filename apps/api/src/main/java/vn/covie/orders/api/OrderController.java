package vn.covie.orders.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.orders.api.dto.CancelOrderRequest;
import vn.covie.orders.api.dto.CancellationRequestBody;
import vn.covie.orders.api.dto.CancellationResponseBody;
import vn.covie.orders.api.dto.OrderCommandResult;
import vn.covie.orders.api.dto.OrderDetailResponse;
import vn.covie.orders.api.dto.ReasonRequest;
import vn.covie.orders.api.dto.IssueRequest;
import vn.covie.orders.application.OrderCancellationService;
import vn.covie.orders.application.OrderCommandService;
import vn.covie.orders.application.OrderCompletionService;
import vn.covie.orders.application.OrderFulfillmentService;
import vn.covie.orders.application.OrderReadService;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderReadService reads; private final OrderCommandService commands;
    private final OrderCancellationService cancellations; private final OrderFulfillmentService fulfillment;
    private final OrderCompletionService completion;
    private final vn.covie.orders.application.OrderIssueService issues;
    private final vn.covie.orders.application.OrderNoShowService noShows;
    public OrderController(OrderReadService reads, OrderCommandService commands, OrderCancellationService cancellations,
            OrderFulfillmentService fulfillment, OrderCompletionService completion, vn.covie.orders.application.OrderIssueService issues, vn.covie.orders.application.OrderNoShowService noShows) {
        this.reads=reads;this.commands=commands;this.cancellations=cancellations;this.fulfillment=fulfillment;this.completion=completion;this.issues=issues;this.noShows=noShows;
    }
    @GetMapping("/{orderId}") public OrderDetailResponse detail(@PathVariable UUID orderId){return reads.detail(orderId);}
    @PostMapping("/{orderId}/confirm") public OrderCommandResult confirm(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return commands.confirm(orderId,key);}
    @PostMapping("/{orderId}/reject") public OrderCommandResult reject(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody ReasonRequest body){return commands.reject(orderId,key,body.reasonCode(),body.note());}
    @PostMapping("/{orderId}/cancel") public OrderCommandResult cancel(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) CancelOrderRequest body){return commands.cancel(orderId,key,body);}
    @PostMapping("/{orderId}/request-cancellation") public OrderCommandResult requestCancellation(@PathVariable UUID orderId,@Valid @RequestBody CancellationRequestBody body){return cancellations.request(orderId,body);}
    @PostMapping("/{orderId}/accept-cancellation") public OrderCommandResult acceptCancellation(@PathVariable UUID orderId,@RequestBody(required=false) CancellationResponseBody body){return cancellations.accept(orderId,body);}
    @PostMapping("/{orderId}/reject-cancellation") public OrderCommandResult rejectCancellation(@PathVariable UUID orderId,@RequestBody(required=false) CancellationResponseBody body){return cancellations.reject(orderId,body);}
    @PostMapping("/{orderId}/mark-out-for-delivery") public ResponseEntity<Void> outForDelivery(@PathVariable UUID orderId){fulfillment.markOutForDelivery(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/mark-delivered") public ResponseEntity<Void> delivered(@PathVariable UUID orderId){fulfillment.markDelivered(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/mark-ready-for-pickup") public ResponseEntity<Void> ready(@PathVariable UUID orderId){fulfillment.markReadyForPickup(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/mark-service-performed") public ResponseEntity<Void> performed(@PathVariable UUID orderId){fulfillment.markServicePerformed(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/confirm-received") public ResponseEntity<Void> received(@PathVariable UUID orderId){completion.confirmReceived(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/confirm-completed") public ResponseEntity<Void> completed(@PathVariable UUID orderId){completion.confirmCompleted(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/report-delivery-issue") public ResponseEntity<Void> deliveryIssue(@PathVariable UUID orderId,@Valid @RequestBody IssueRequest request){issues.reportDeliveryIssue(orderId,request);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/report-service-issue") public ResponseEntity<Void> serviceIssue(@PathVariable UUID orderId,@Valid @RequestBody IssueRequest request){issues.reportServiceIssue(orderId,request);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/report-buyer-no-show") public ResponseEntity<Void> noShow(@PathVariable UUID orderId){noShows.reportBuyerNoShow(orderId);return ResponseEntity.ok().build();}
    @PostMapping("/{orderId}/contest-no-show") public ResponseEntity<Void> contestNoShow(@PathVariable UUID orderId,@RequestBody(required=false) CancellationResponseBody body){noShows.contest(orderId,body==null?null:body.note());return ResponseEntity.ok().build();}
}
