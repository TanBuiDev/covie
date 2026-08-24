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
    private final vn.covie.orders.application.OrderCommandIdempotencyService idempotency;
    public OrderController(OrderReadService reads, OrderCommandService commands, OrderCancellationService cancellations,
            OrderFulfillmentService fulfillment, OrderCompletionService completion, vn.covie.orders.application.OrderIssueService issues, vn.covie.orders.application.OrderNoShowService noShows, vn.covie.orders.application.OrderCommandIdempotencyService idempotency) {
        this.reads=reads;this.commands=commands;this.cancellations=cancellations;this.fulfillment=fulfillment;this.completion=completion;this.issues=issues;this.noShows=noShows;this.idempotency=idempotency;
    }
    @GetMapping("/{orderId}") public OrderDetailResponse detail(@PathVariable UUID orderId){return reads.detail(orderId);}
    @GetMapping("/{orderId}/events") public java.util.List<vn.covie.orders.api.dto.OrderTimelineEventResponse> events(@PathVariable UUID orderId){return reads.events(orderId);}
    @PostMapping("/{orderId}/confirm") public OrderCommandResult confirm(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return commands.confirm(orderId,key);}
    @PostMapping("/{orderId}/reject") public OrderCommandResult reject(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody ReasonRequest body){return commands.reject(orderId,key,body.reasonCode(),body.note());}
    @PostMapping("/{orderId}/cancel") public OrderCommandResult cancel(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody(required=false) CancelOrderRequest body){return commands.cancel(orderId,key,body);}
    @PostMapping("/{orderId}/request-cancellation") public OrderCommandResult requestCancellation(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody CancellationRequestBody body){return idempotency.execute(orderId,"request-cancellation",key,body,OrderCommandResult.class,()->cancellations.request(orderId,body));}
    @PostMapping("/{orderId}/accept-cancellation") public OrderCommandResult acceptCancellation(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) CancellationResponseBody body){return idempotency.execute(orderId,"accept-cancellation",key,body==null?new CancellationResponseBody(null):body,OrderCommandResult.class,()->cancellations.accept(orderId,body));}
    @PostMapping("/{orderId}/reject-cancellation") public OrderCommandResult rejectCancellation(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) CancellationResponseBody body){return idempotency.execute(orderId,"reject-cancellation",key,body==null?new CancellationResponseBody(null):body,OrderCommandResult.class,()->cancellations.reject(orderId,body));}
    @PostMapping("/{orderId}/mark-out-for-delivery") public ResponseEntity<Void> outForDelivery(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"mark-out-for-delivery",key,()->fulfillment.markOutForDelivery(orderId));}
    @PostMapping("/{orderId}/mark-delivered") public ResponseEntity<Void> delivered(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"mark-delivered",key,()->fulfillment.markDelivered(orderId));}
    @PostMapping("/{orderId}/mark-ready-for-pickup") public ResponseEntity<Void> ready(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"mark-ready-for-pickup",key,()->fulfillment.markReadyForPickup(orderId));}
    @PostMapping("/{orderId}/mark-service-performed") public ResponseEntity<Void> performed(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"mark-service-performed",key,()->fulfillment.markServicePerformed(orderId));}
    @PostMapping("/{orderId}/confirm-received") public ResponseEntity<Void> received(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"confirm-received",key,()->completion.confirmReceived(orderId));}
    @PostMapping("/{orderId}/confirm-completed") public ResponseEntity<Void> completed(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"confirm-completed",key,()->completion.confirmCompleted(orderId));}
    @PostMapping("/{orderId}/report-delivery-issue") public ResponseEntity<Void> deliveryIssue(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody IssueRequest request){return voidCommand(orderId,"report-delivery-issue",key,request,()->issues.reportDeliveryIssue(orderId,request));}
    @PostMapping("/{orderId}/report-service-issue") public ResponseEntity<Void> serviceIssue(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody IssueRequest request){return voidCommand(orderId,"report-service-issue",key,request,()->issues.reportServiceIssue(orderId,request));}
    @PostMapping("/{orderId}/report-buyer-no-show") public ResponseEntity<Void> noShow(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key){return voidCommand(orderId,"report-buyer-no-show",key,()->noShows.reportBuyerNoShow(orderId));}
    @PostMapping("/{orderId}/contest-no-show") public ResponseEntity<Void> contestNoShow(@PathVariable UUID orderId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) CancellationResponseBody body){return voidCommand(orderId,"contest-no-show",key,body==null?new CancellationResponseBody(null):body,()->noShows.contest(orderId,body==null?null:body.note()));}
    private ResponseEntity<Void> voidCommand(UUID orderId,String command,String key,Runnable action){return voidCommand(orderId,command,key,java.util.Map.of("orderId",orderId),action);}
    private ResponseEntity<Void> voidCommand(UUID orderId,String command,String key,Object body,Runnable action){idempotency.executeVoid(orderId,command,key,body,action);return ResponseEntity.ok().build();}
}
