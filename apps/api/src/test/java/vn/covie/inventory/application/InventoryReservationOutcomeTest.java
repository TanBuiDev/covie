package vn.covie.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.inventory.model.InventoryReservationEntity;
import vn.covie.inventory.model.InventoryReservationStatus;
import vn.covie.inventory.persistence.InventoryMovementRepository;
import vn.covie.inventory.persistence.InventoryReservationRepository;
import vn.covie.shared.id.UuidV7Generator;

class InventoryReservationOutcomeTest {
    @Test
    void releasingActiveOrderReservationRestoresOnlyReservedQuantity() {
        UUID orderId = UUID.randomUUID(); UUID variantId = UUID.randomUUID();
        InventoryBalanceEntity balance = InventoryBalanceEntity.zero(variantId, Instant.EPOCH);
        balance.adjust(5, 2, Instant.EPOCH);
        InventoryReservationEntity reservation = InventoryReservationEntity.active(UUID.randomUUID(), variantId, orderId,
                UUID.randomUUID(), 2, Instant.EPOCH);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        when(reservations.findActiveByOrderIdOrdered(orderId)).thenReturn(List.of(reservation));
        InventoryLockService locks = mock(InventoryLockService.class);
        when(locks.lockTrackedBalances(Set.of(variantId))).thenReturn(List.of(balance));
        InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
        InventoryReservationService service = new InventoryReservationService(reservations, movements, locks,
                new UuidV7Generator(), () -> Instant.parse("2026-08-22T01:00:00Z"));

        service.releaseActiveForOrder(orderId, UUID.randomUUID(), "ORDER_CANCELLED", Instant.parse("2026-08-22T01:00:00Z"));

        assertThat(balance.getStockOnHand()).isEqualTo(5);
        assertThat(balance.getReservedQuantity()).isZero();
        assertThat(reservation.getReservationStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        verify(movements).save(org.mockito.ArgumentMatchers.any());
    }
}
