package vn.covie.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCancellationRequestTest {
    @Test
    void acceptedRequestRecordsTheMerchantResponseOnce() {
        OrderCancellationRequestEntity request = OrderCancellationRequestEntity.pending(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "CHANGE_OF_MIND", "Please cancel", OrderStatus.CONFIRMED, Instant.EPOCH);

        request.accept(UUID.randomUUID(), "Accepted", Instant.EPOCH.plusSeconds(1));

        assertThat(request.getRequestStatus()).isEqualTo(CancellationRequestStatus.ACCEPTED);
        assertThat(request.getRespondedAt()).isEqualTo(Instant.EPOCH.plusSeconds(1));
    }
}
