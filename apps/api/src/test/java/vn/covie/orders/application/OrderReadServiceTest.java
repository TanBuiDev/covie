package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.commerce.PaymentMethod;
import com.fasterxml.jackson.databind.node.NullNode;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.reviews.application.ReviewPolicyProvider;
import vn.covie.reviews.persistence.ReviewRepository;

class OrderReadServiceTest {
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

    @Test
    void exposesReviewEligibilityOnEachBuyerOrderItem() {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        UUID id = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(id, "CVREVIEW", OrderType.PRODUCT, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), PaymentMethod.COD, 10000, OrderShippingFeeType.FREE, 0L, 10000, 10000L,
                "VND", now.plusSeconds(60), now);
        order.complete(now.plusSeconds(1));
        OrderItemEntity item = OrderItemEntity.create(UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CatalogItemType.PRODUCT, 1, 10000, "VND", NullNode.getInstance(), now);
        OrderRepository orders = mock(OrderRepository.class); when(orders.findById(id)).thenReturn(Optional.of(order));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class); when(access.requireVisible(order)).thenReturn(
                new OrderAccess(order.getBuyerUserId(), OrderActorType.BUYER, id, order.getMerchantProfileId(), order.getMerchantLocationId()));
        OrderItemRepository items = mock(OrderItemRepository.class);
        when(items.findAllByOrderIdOrderByCreatedAtAscIdAsc(id)).thenReturn(java.util.List.of(item));
        ReviewRepository reviews = mock(ReviewRepository.class); when(reviews.findAllByOrderId(id)).thenReturn(java.util.List.of());
        ReviewPolicyProvider reviewPolicy = mock(ReviewPolicyProvider.class); when(reviewPolicy.creationEnabled()).thenReturn(true);
        OrderReadService service = new OrderReadService(orders, access, mock(OrderAllowedActionService.class),
                mock(MerchantAccessPolicy.class), null, null, null, items, null, null, () -> now, null, reviews, reviewPolicy);

        var detail = service.detail(id);

        assertThat(detail.items()).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(item.getId());
            assertThat(result.reviewEligibility().eligible()).isTrue();
            assertThat(result.reviewEligibility().reason()).isEqualTo("ELIGIBLE");
        });
    }
}
