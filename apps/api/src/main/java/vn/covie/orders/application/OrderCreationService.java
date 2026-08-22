package vn.covie.orders.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.application.RevalidatedCheckout;
import vn.covie.checkout.application.RevalidatedCheckoutItem;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventEntity;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.model.ServiceFulfillmentEntity;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.persistence.ServiceFulfillmentRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;

@Service
public class OrderCreationService {
    private static final int ORDER_CODE_ATTEMPTS = 5;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductFulfillmentRepository productFulfillments;
    private final ServiceFulfillmentRepository serviceFulfillments;
    private final OrderEventRepository events;
    private final OrderCodeGenerator orderCodes;
    private final OrderSnapshotFactory snapshots;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final OrderPolicyProvider policyProvider;

    public OrderCreationService(OrderRepository orders, OrderItemRepository items,
            ProductFulfillmentRepository productFulfillments, ServiceFulfillmentRepository serviceFulfillments,
            OrderEventRepository events, OrderCodeGenerator orderCodes, OrderSnapshotFactory snapshots,
            UuidV7Generator ids, AppClock clock, OrderPolicyProvider policyProvider) {
        this.orders = orders; this.items = items; this.productFulfillments = productFulfillments;
        this.serviceFulfillments = serviceFulfillments; this.events = events; this.orderCodes = orderCodes;
        this.snapshots = snapshots; this.ids = ids; this.clock = clock; this.policyProvider = policyProvider;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CreatedOrder createPending(RevalidatedCheckout checkout) {
        Instant now = clock.now();
        OrderType type = checkout.candidate().itemType() == CatalogItemType.PRODUCT ? OrderType.PRODUCT : OrderType.SERVICE;
        OrderMoney money = snapshots.money(type, checkout.fulfillmentMethod(), checkout.subtotal(), checkout.shipping());
        UUID orderId = ids.generate();
        String code = nextAvailableCode();
        Instant confirmationExpiresAt = now.plus(policyProvider.resolve(
                checkout.candidate().merchantProfileId(), checkout.candidate().merchantLocationId()).confirmationTimeout());
        OrderEntity order = OrderEntity.pending(orderId, code, type, checkout.candidate().buyerUserId(),
                checkout.candidate().merchantProfileId(), checkout.candidate().merchantLocationId(), checkout.paymentMethod(),
                checkout.subtotal(), money.shippingFeeType(), money.shippingFee(), money.knownTotal(), money.totalAmount(),
                checkout.currency(), confirmationExpiresAt, now);
        orders.save(order);

        Map<UUID, UUID> itemIdsByOffer = new LinkedHashMap<>();
        for (RevalidatedCheckoutItem line : checkout.items().stream()
                .sorted(Comparator.comparing(item -> item.candidateItem().locationVariantId())).toList()) {
            UUID orderItemId = ids.generate();
            var candidate = line.candidateItem();
            items.save(vn.covie.orders.model.OrderItemEntity.create(orderItemId, orderId, candidate.catalogItemId(),
                    candidate.variantId(), candidate.locationVariantId(), checkout.candidate().itemType(), candidate.quantity(),
                    line.unitPrice(), checkout.currency(), line.itemSnapshot(), now));
            itemIdsByOffer.put(candidate.locationVariantId(), orderItemId);
        }

        createFulfillment(orderId, checkout, now);
        events.save(OrderEventEntity.created(ids.generate(), orderId, checkout.candidate().buyerUserId(),
                JsonNodeFactory.instance.objectNode().put("sourceType", checkout.candidate().sourceType().name()), now));
        return new CreatedOrder(orderId, code, Map.copyOf(itemIdsByOffer));
    }

    private String nextAvailableCode() {
        for (int attempt = 0; attempt < ORDER_CODE_ATTEMPTS; attempt++) {
            String code = orderCodes.generate();
            if (!orders.existsByOrderCode(code)) return code;
        }
        throw new IllegalStateException("Could not allocate a unique order code.");
    }

    private void createFulfillment(UUID orderId, RevalidatedCheckout checkout, Instant now) {
        var snapshots = checkout.fulfillmentSnapshots();
        if (checkout.candidate().itemType() == CatalogItemType.SERVICE) {
            serviceFulfillments.save(ServiceFulfillmentEntity.create(orderId, snapshots.buyerAddressId(), snapshots.buyerAddress(),
                    snapshots.merchantLocation(), snapshots.buyerContact(), snapshots.merchantContact(), now));
            return;
        }
        ProductFulfillmentMethod method = checkout.fulfillmentMethod() == FulfillmentMethod.PICKUP
                ? ProductFulfillmentMethod.PICKUP : ProductFulfillmentMethod.SELLER_DELIVERY;
        productFulfillments.save(ProductFulfillmentEntity.create(orderId, method, checkout.candidate().merchantLocationId(),
                snapshots.buyerAddressId(), snapshots.distanceMeters(), snapshots.merchantLocation(), snapshots.buyerAddress(),
                snapshots.buyerContact(), snapshots.merchantContact(), snapshots.shippingPolicy()));
    }
}
