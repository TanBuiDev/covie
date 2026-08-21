package vn.covie.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.inventory.model.InventoryMovementEntity;
import vn.covie.inventory.model.InventoryMovementType;
import vn.covie.inventory.model.InventoryReservationEntity;
import vn.covie.inventory.persistence.InventoryMovementRepository;
import vn.covie.inventory.persistence.InventoryReservationRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class InventoryReservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void reservesTrackedItemAndWritesReservationAndMovement() {
        UUID offerId = UUID.randomUUID();
        InventoryBalanceEntity balance = balance(offerId, 5);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
        InventoryReservationService service = new InventoryReservationService(reservations, movements,
                new UuidV7Generator(), () -> NOW);

        service.reserve(List.of(new ReservationLine(offerId, UUID.randomUUID(), UUID.randomUUID(), 3)),
                Map.of(offerId, balance), UUID.randomUUID());

        ArgumentCaptor<InventoryReservationEntity> savedReservation = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        ArgumentCaptor<InventoryMovementEntity> savedMovement = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(reservations).save(savedReservation.capture());
        verify(movements).save(savedMovement.capture());
        assertThat(savedReservation.getValue().getQuantity()).isEqualTo(3);
        assertThat(savedMovement.getValue().getMovementType()).isEqualTo(InventoryMovementType.RESERVE);
        assertThat(savedMovement.getValue().getQuantity()).isEqualTo(3);
        assertThat(balance.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void failsBeforeMutatingOrPersistingAnyLineWhenOneLineIsInsufficient() {
        UUID firstOffer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondOffer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InventoryBalanceEntity first = balance(firstOffer, 5);
        InventoryBalanceEntity second = balance(secondOffer, 1);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
        InventoryReservationService service = new InventoryReservationService(reservations, movements,
                new UuidV7Generator(), () -> NOW);

        assertThatThrownBy(() -> service.reserve(List.of(
                new ReservationLine(firstOffer, UUID.randomUUID(), UUID.randomUUID(), 2),
                new ReservationLine(secondOffer, UUID.randomUUID(), UUID.randomUUID(), 2)),
                Map.of(firstOffer, first, secondOffer, second), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);

        verify(reservations, never()).save(org.mockito.ArgumentMatchers.any());
        verify(movements, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(first.getReservedQuantity()).isZero();
        assertThat(second.getReservedQuantity()).isZero();
    }

    private static InventoryBalanceEntity balance(UUID offerId, long stockOnHand) {
        InventoryBalanceEntity balance = InventoryBalanceEntity.zero(offerId, NOW);
        balance.adjust(stockOnHand, 0, NOW);
        return balance;
    }
}
