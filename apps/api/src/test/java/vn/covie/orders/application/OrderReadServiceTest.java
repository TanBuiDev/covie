package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.model.ServiceFulfillmentEntity;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.persistence.ServiceFulfillmentRepository;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderCancellationRequestRepository;
import vn.covie.orders.persistence.OrderIssueRepository;
import vn.covie.reputation.application.ReputationReadService;
import vn.covie.reviews.api.dto.ReviewEligibilityReason;
import vn.covie.reviews.api.dto.ReviewEligibilityResponse;
import vn.covie.reviews.application.OrderReviewEligibilityService;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.commerce.PaymentMethod;

class OrderReadServiceTest {
    @Test
    void buyerSummariesUseStoredItemNamesWithOneBulkRead() {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        UUID buyer = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVITEM", OrderType.PRODUCT, buyer,
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 100, OrderShippingFeeType.FREE,
                0L, 100, 100L, "VND", now.plusSeconds(60), now);
        var snapshot = new ObjectMapper().createObjectNode().put("name", "Tên lúc đặt").put("privateField", "not exposed");
        var item = vn.covie.orders.model.OrderItemEntity.create(UUID.randomUUID(), order.getId(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), vn.covie.catalog.model.CatalogItemType.PRODUCT,
                2, 50, "VND", snapshot, now);
        OrderItemRepository itemRepository = mock(OrderItemRepository.class, invocation ->
                invocation.getMethod().getName().equals("findAllByOrderIdInOrderByCreatedAtAscIdAsc") ? List.of(item)
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findAllByBuyerUserIdOrderByCreatedAtDesc(buyer)).thenReturn(List.of(order));
        var service = new OrderReadService(orders, mock(OrderAccessPolicy.class), mock(OrderAllowedActionService.class),
                mock(MerchantAccessPolicy.class), null, null, null, itemRepository, null, null, () -> now, null);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(service.myOrders(buyer).items().getFirst());
        assertThat(json.path("items").path(0).path("name").asText()).isEqualTo("Tên lúc đặt");
        assertThat(json.path("items").path(0).path("quantity").asLong()).isEqualTo(2);
        assertThat(json.toString()).doesNotContain("privateField", "not exposed");
        assertThat(org.mockito.Mockito.mockingDetails(itemRepository).getInvocations()).hasSize(1);
    }

    @Test
    void buyerSummaryKeepsStoredPricingAndUnknownNegotiatedTotal() {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        UUID buyerId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVSUMMARY", OrderType.PRODUCT, buyerId,
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 100_000,
                OrderShippingFeeType.NEGOTIATED, null, 100_000, null, "VND", now.plusSeconds(60), now);
        order.confirm(now.plusSeconds(10));
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findAllByBuyerUserIdOrderByCreatedAtDesc(buyerId)).thenReturn(List.of(order));
        OrderReadService service = new OrderReadService(orders, mock(OrderAccessPolicy.class),
                mock(OrderAllowedActionService.class), mock(MerchantAccessPolicy.class), () -> now);
        var summary = new ObjectMapper().findAndRegisterModules().valueToTree(service.myOrders(buyerId).items().getFirst());
        assertThat(summary.path("pricing").path("knownTotal").asLong()).isEqualTo(100_000);
        assertThat(summary.path("pricing").path("shippingFeeType").asText()).isEqualTo("NEGOTIATED");
        assertThat(summary.path("pricing").path("totalAmount").isNull()).isTrue();
        assertThat(summary.path("pricing").path("currency").asText()).isEqualTo("VND");
        assertThat(summary.has("updatedAt")).isTrue();
    }

    @Test
    void exposesReviewEligibilityOnlyToTheBuyerView() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "CVREVIEW", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 100, OrderShippingFeeType.FREE,
                0L, 100, 100L, "VND", Instant.now().plusSeconds(60), Instant.now());
        order.complete(Instant.now());
        var item = vn.covie.orders.model.OrderItemEntity.create(UUID.randomUUID(), orderId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), vn.covie.catalog.model.CatalogItemType.PRODUCT,
                1, 100, "VND", new ObjectMapper().createObjectNode(), Instant.now());
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class);
        when(access.requireVisible(order)).thenReturn(new OrderAccess(order.getBuyerUserId(), OrderActorType.BUYER,
                orderId, order.getMerchantProfileId(), order.getMerchantLocationId()));
        OrderItemRepository items = mock(OrderItemRepository.class);
        when(items.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId)).thenReturn(List.of(item));
        OrderReviewEligibilityService eligibility = mock(OrderReviewEligibilityService.class);
        var eligible = new ReviewEligibilityResponse(item.getId(), true, ReviewEligibilityReason.ELIGIBLE);
        when(eligibility.evaluate(order, List.of(item))).thenReturn(Map.of(item.getId(), eligible));
        OrderReadService service = new OrderReadService(orders, access, mock(OrderAllowedActionService.class),
                mock(MerchantAccessPolicy.class), mock(ProductFulfillmentRepository.class),
                mock(ServiceFulfillmentRepository.class), mock(OrderEventRepository.class), items,
                mock(OrderCancellationRequestRepository.class), mock(OrderIssueRepository.class), Instant::now,
                mock(ReputationReadService.class), eligibility);

        assertThat(service.detail(orderId).items()).singleElement()
                .extracting(response -> response.reviewEligibility()).isEqualTo(eligible);

        when(access.requireVisible(order)).thenReturn(new OrderAccess(UUID.randomUUID(), OrderActorType.MERCHANT,
                orderId, order.getMerchantProfileId(), order.getMerchantLocationId()));
        assertThat(service.detail(orderId).items()).singleElement()
                .extracting(response -> response.reviewEligibility()).isNull();
        org.mockito.Mockito.verify(eligibility, org.mockito.Mockito.times(1)).evaluate(order, List.of(item));
    }

    @Test
    void exposesStoredProductShippingPolicyAndServiceDistance() {
        ObjectMapper mapper = new ObjectMapper();
        UUID productId = UUID.randomUUID();
        OrderEntity productOrder = OrderEntity.pending(productId, "CVPRODUCT", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FIXED, 2_000L,
                12_000, 12_000L, "VND", Instant.now().plusSeconds(60), Instant.now());
        UUID serviceId = UUID.randomUUID();
        OrderEntity serviceOrder = OrderEntity.pending(serviceId, "CVSERVICE", OrderType.SERVICE, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.NOT_APPLICABLE,
                null, 10_000, 10_000L, "VND", Instant.now().plusSeconds(60), Instant.now());
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findById(productId)).thenReturn(Optional.of(productOrder));
        when(orders.findById(serviceId)).thenReturn(Optional.of(serviceOrder));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class);
        when(access.requireVisible(productOrder)).thenReturn(new OrderAccess(productOrder.getBuyerUserId(),
                OrderActorType.BUYER, productId, productOrder.getMerchantProfileId(), productOrder.getMerchantLocationId()));
        when(access.requireVisible(serviceOrder)).thenReturn(new OrderAccess(serviceOrder.getBuyerUserId(),
                OrderActorType.BUYER, serviceId, serviceOrder.getMerchantProfileId(), serviceOrder.getMerchantLocationId()));
        OrderAllowedActionService actions = mock(OrderAllowedActionService.class);
        when(actions.allowedActions(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of());
        ProductFulfillmentRepository products = mock(ProductFulfillmentRepository.class);
        var policySnapshot = mapper.createObjectNode().put("policyId", "policy-1");
        when(products.findById(productId)).thenReturn(Optional.of(ProductFulfillmentEntity.create(productId,
                ProductFulfillmentMethod.SELLER_DELIVERY, productOrder.getMerchantLocationId(), UUID.randomUUID(),
                1_250, mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(),
                mapper.createObjectNode(), policySnapshot)));
        ServiceFulfillmentRepository services = mock(ServiceFulfillmentRepository.class);
        var providerLocation = mapper.createObjectNode().put("name", "Provider location");
        when(services.findById(serviceId)).thenReturn(Optional.of(ServiceFulfillmentEntity.create(serviceId,
                UUID.randomUUID(), 2_500, mapper.createObjectNode(), providerLocation,
                mapper.createObjectNode(), mapper.createObjectNode(), Instant.now())));
        OrderReadService service = new OrderReadService(orders, access, actions, mock(MerchantAccessPolicy.class),
                products, services, mock(OrderEventRepository.class), mock(OrderItemRepository.class),
                mock(OrderCancellationRequestRepository.class), mock(OrderIssueRepository.class), Instant::now,
                mock(ReputationReadService.class));

        assertThat(service.detail(productId).fulfillment().shippingPolicy()).isEqualTo(Map.of("policyId", "policy-1"));
        assertThat(service.detail(productId).fulfillment().distanceMeters()).isEqualTo(1_250);
        assertThat(service.detail(serviceId).fulfillment().distanceMeters()).isEqualTo(2_500);
        assertThat(service.detail(serviceId).fulfillment().providerLocation()).isEqualTo(Map.of("name", "Provider location"));
        assertThat(service.detail(serviceId).fulfillment().shippingPolicy()).isNull();
    }

    @Test
    void mapsVisibleOrderToAnActorSpecificDetailResponse() {
        UUID id=UUID.randomUUID(); OrderEntity order=OrderEntity.pending(id,"CVTEST",OrderType.PRODUCT,UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),PaymentMethod.COD,10000,OrderShippingFeeType.FREE,0L,10000,10000L,"VND",Instant.now().plusSeconds(60),Instant.now());
        OrderRepository orders=mock(OrderRepository.class); when(orders.findById(id)).thenReturn(Optional.of(order));
        OrderAccessPolicy access=mock(OrderAccessPolicy.class); when(access.requireVisible(order)).thenReturn(new OrderAccess(order.getBuyerUserId(),OrderActorType.BUYER,id,order.getMerchantProfileId(),order.getMerchantLocationId()));
        OrderReadService service=new OrderReadService(orders,access,mock(OrderAllowedActionService.class),mock(MerchantAccessPolicy.class),()->Instant.now());

        var detail=service.detail(id);

        assertThat(detail.id()).isEqualTo(id); assertThat(detail.orderCode()).isEqualTo("CVTEST");
    }

    @Test
    void listsOrdersForAnAuthorizedMerchant() {
        UUID merchantId=UUID.randomUUID(); OrderEntity order=OrderEntity.pending(UUID.randomUUID(),"CVSELL",OrderType.PRODUCT,UUID.randomUUID(),
                merchantId,UUID.randomUUID(),PaymentMethod.COD,10000,OrderShippingFeeType.FREE,0L,10000,10000L,"VND",Instant.now().plusSeconds(60),Instant.now());
        OrderRepository orders=mock(OrderRepository.class); when(orders.findAllByMerchantProfileIdOrderByCreatedAtDesc(merchantId)).thenReturn(java.util.List.of(order));
        MerchantAccessPolicy merchantAccess=mock(MerchantAccessPolicy.class);
        OrderReadService service=new OrderReadService(orders,mock(OrderAccessPolicy.class),mock(OrderAllowedActionService.class),merchantAccess,()->Instant.now());

        var page=service.merchantOrders(merchantId);

        assertThat(page.items()).singleElement().extracting(item->item.orderCode()).isEqualTo("CVSELL");
        org.mockito.Mockito.verify(merchantAccess).require(merchantId,vn.covie.merchants.security.MerchantCapability.VIEW_ORDERS);
    }
}
