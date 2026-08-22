package vn.covie.inventory.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.inventory.model.InventoryMovementEntity;
import vn.covie.inventory.model.InventoryMovementType;
import vn.covie.inventory.model.InventoryReservationEntity;
import vn.covie.inventory.persistence.InventoryMovementRepository;
import vn.covie.inventory.persistence.InventoryReservationRepository;
import vn.covie.shared.id.UuidV7Generator;

@Service
public class InventoryReservationService {
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final InventoryLockService locks;
    private final UuidV7Generator ids;
    private final vn.covie.shared.time.AppClock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryReservationService(InventoryReservationRepository reservations, InventoryMovementRepository movements,
            InventoryLockService locks, UuidV7Generator ids, vn.covie.shared.time.AppClock clock) {
        this.reservations = reservations;
        this.movements = movements;
        this.locks = locks;
        this.ids = ids;
        this.clock = clock;
    }

    public InventoryReservationService(InventoryReservationRepository reservations, InventoryMovementRepository movements,
            UuidV7Generator ids, vn.covie.shared.time.AppClock clock) {
        this(reservations, movements, null, ids, clock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<ReservationLine> lines, Map<UUID, InventoryBalanceEntity> lockedBalances, UUID actorUserId) {
        List<ReservationLine> orderedLines = lines.stream()
                .sorted(Comparator.comparing(ReservationLine::locationVariantId).thenComparing(ReservationLine::orderItemId))
                .toList();
        Map<UUID, Long> requestedByOffer = orderedLines.stream().collect(java.util.stream.Collectors.groupingBy(
                ReservationLine::locationVariantId, java.util.stream.Collectors.summingLong(ReservationLine::quantity)));
        requestedByOffer.forEach((offerId, quantity) -> {
            InventoryBalanceEntity balance = lockedBalances.get(offerId);
            if (balance == null) {
                throw new IllegalStateException("Missing locked inventory balance: " + offerId);
            }
            if (quantity <= 0 || balance.availableQuantity() < quantity) {
                balance.reserve(quantity, clock.now());
            }
        });

        Instant now = clock.now();
        for (ReservationLine line : orderedLines) {
            InventoryBalanceEntity balance = lockedBalances.get(line.locationVariantId());
            balance.reserve(line.quantity(), now);
            InventoryReservationEntity reservation = InventoryReservationEntity.active(ids.generate(), line.locationVariantId(),
                    line.orderId(), line.orderItemId(), line.quantity(), now);
            reservations.save(reservation);
            movements.save(InventoryMovementEntity.create(ids.generate(), line.locationVariantId(), InventoryMovementType.RESERVE,
                    line.quantity(), line.orderId(), line.orderItemId(), reservation.getId(), actorUserId,
                    "CHECKOUT_RESERVATION", null, now));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseActiveForOrder(UUID orderId, UUID actorUserId, String reasonCode, Instant now) {
        List<InventoryReservationEntity> active = reservations.findActiveByOrderIdOrdered(orderId);
        if (active.isEmpty()) return;
        Map<UUID, InventoryBalanceEntity> balances = lockedBalances(active);
        for (InventoryReservationEntity reservation : active) {
            balances.get(reservation.getLocationVariantId()).releaseReserved(reservation.getQuantity(), now);
            reservation.markReleased(now);
            movements.save(InventoryMovementEntity.create(ids.generate(), reservation.getLocationVariantId(),
                    InventoryMovementType.RELEASE_RESERVATION, reservation.getQuantity(), orderId,
                    reservation.getOrderItemId(), reservation.getId(), actorUserId, reasonCode, null, now));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitActiveForOrder(UUID orderId, UUID actorUserId, Instant now) {
        List<InventoryReservationEntity> active = reservations.findActiveByOrderIdOrdered(orderId);
        if (active.isEmpty()) return;
        Map<UUID, InventoryBalanceEntity> balances = lockedBalances(active);
        for (InventoryReservationEntity reservation : active) {
            balances.get(reservation.getLocationVariantId()).commitReservedSale(reservation.getQuantity(), now);
            reservation.markCommitted(now);
            movements.save(InventoryMovementEntity.create(ids.generate(), reservation.getLocationVariantId(),
                    InventoryMovementType.SALE, reservation.getQuantity(), orderId, reservation.getOrderItemId(),
                    reservation.getId(), actorUserId, "ORDER_COMPLETED", null, now));
        }
    }

    private Map<UUID, InventoryBalanceEntity> lockedBalances(List<InventoryReservationEntity> reservationsForOrder) {
        if (locks == null) throw new IllegalStateException("Inventory locks are required for reservation outcomes.");
        return locks.lockTrackedBalances(reservationsForOrder.stream()
                .map(InventoryReservationEntity::getLocationVariantId).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(InventoryBalanceEntity::getLocationVariantId, balance -> balance));
    }
}
