package vn.covie.orders.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;

class OrderAccessPolicyTest {
    @Test
    void classifiesTheBuyerOfAnOrderWithoutMerchantAuthorization() {
        UUID buyer = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVTEST", OrderType.PRODUCT, buyer,
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", Instant.now().plusSeconds(1800), Instant.now());
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(buyer, Set.of(SystemRole.USER), UUID.randomUUID()));

        OrderAccess access = new OrderAccessPolicy(actors, mock(MerchantAccessPolicy.class)).requireVisible(order);

        assertThat(access.actorType()).isEqualTo(OrderActorType.BUYER);
        assertThat(access.actorUserId()).isEqualTo(buyer);
    }
}
