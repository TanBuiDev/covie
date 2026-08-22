package vn.covie.orders.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.orders.api.dto.OrderPageResponse;
import vn.covie.orders.application.OrderReadService;

@RestController
@RequestMapping("/api/v1/me/orders")
public class MeOrderController {
    private final OrderReadService reads; private final CurrentActorProvider actors;
    public MeOrderController(OrderReadService reads,CurrentActorProvider actors){this.reads=reads;this.actors=actors;}
    @GetMapping public OrderPageResponse list(){return reads.myOrders(actors.currentActor().userId());}
}
