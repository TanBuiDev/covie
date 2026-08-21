package vn.covie.inventory.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class InventoryBalanceEntityTest {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void reservesOnlyAvailableQuantity() {
        InventoryBalanceEntity balance = InventoryBalanceEntity.zero(UUID.randomUUID(), NOW);
        balance.adjust(5, 0, NOW);

        balance.reserve(3, NOW.plusSeconds(1));

        assertThat(balance.getReservedQuantity()).isEqualTo(3);
        assertThat(balance.availableQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsReservationThatExceedsAvailability() {
        InventoryBalanceEntity balance = InventoryBalanceEntity.zero(UUID.randomUUID(), NOW);
        balance.adjust(2, 0, NOW);

        assertThatThrownBy(() -> balance.reserve(3, NOW.plusSeconds(1)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);
    }
}
