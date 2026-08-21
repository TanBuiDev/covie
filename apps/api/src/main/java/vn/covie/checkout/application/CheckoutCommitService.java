package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.cart.model.CartEntity;
import vn.covie.cart.persistence.CartItemRepository;
import vn.covie.cart.persistence.CartRepository;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.api.dto.OrderCreatedResponse;
import vn.covie.inventory.application.InventoryLockService;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.inventory.application.ReservationLine;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.orders.application.OrderCreationService;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CheckoutCommitService {
    private final CheckoutSessionService sessions;
    private final CheckoutPreviewService previews;
    private final CheckoutComparisonService comparison;
    private final OrderCreationService orders;
    private final InventoryLockService inventoryLocks;
    private final InventoryReservationService reservations;
    private final OutboxEventWriter outbox;
    private final ObjectMapper mapper;
    private final AppClock clock;
    private final CartRepository carts;
    private final CartItemRepository cartItems;

    public CheckoutCommitService(CheckoutSessionService sessions, CheckoutPreviewService previews,
            CheckoutComparisonService comparison, OrderCreationService orders, InventoryLockService inventoryLocks,
            InventoryReservationService reservations, OutboxEventWriter outbox, ObjectMapper mapper, AppClock clock,
            CartRepository carts, CartItemRepository cartItems) {
        this.sessions = sessions; this.previews = previews; this.comparison = comparison; this.orders = orders;
        this.inventoryLocks = inventoryLocks; this.reservations = reservations; this.outbox = outbox;
        this.mapper = mapper; this.clock = clock; this.carts = carts; this.cartItems = cartItems;
    }

    @Transactional
    public OrderCreatedResponse commit(UUID userId, String checkoutToken) {
        var session = sessions.lock(checkoutToken, userId);
        CheckoutPreviewRequest request;
        CheckoutComparableSnapshot accepted;
        try {
            request = mapper.treeToValue(session.getRequestSnapshot(), CheckoutPreviewRequest.class);
            accepted = mapper.treeToValue(session.getPreviewSnapshot(), CheckoutComparableSnapshot.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.CHECKOUT_SESSION_INVALID, HttpStatus.CONFLICT,
                    "Checkout session is invalid.");
        }
        CartEntity cart = lockCartIfRequired(userId, session, request);
        CheckoutRevalidation initial = previews.revalidate(userId, request);
        Map<UUID, InventoryBalanceEntity> lockedBalances = lockTrackedBalances(initial);
        CheckoutRevalidation revalidated = previews.revalidate(userId, request, lockedBalances);
        List<CheckoutChange> changes = comparison.compare(accepted, revalidated.comparableSnapshot());
        if (!changes.isEmpty()) {
            throw new ApiException(ErrorCode.CHECKOUT_CHANGED, HttpStatus.CONFLICT, "Checkout has changed.",
                    Map.of("changes", changes));
        }
        var created = orders.createPending(revalidated.checkout());
        reserveTrackedItems(userId, revalidated, created, lockedBalances);
        outbox.append(new OutboxEventIntent("ORDER_CREATED", "ORDER", created.id(),
                Map.of("orderId", created.id(), "orderCode", created.orderCode())));
        Instant now = clock.now();
        session.consume(now);
        if (cart != null) {
            cartItems.deleteAllByCartId(cart.getId());
            cart.checkOut(now);
        }
        return new OrderCreatedResponse(created.id(), created.orderCode(), vn.covie.orders.model.OrderStatus.PENDING_CONFIRMATION, now);
    }

    private CartEntity lockCartIfRequired(UUID userId, vn.covie.checkout.model.CheckoutSessionEntity session,
            CheckoutPreviewRequest request) {
        if (session.getSourceType() != vn.covie.checkout.model.CheckoutSourceType.CART || request.cartId() == null) {
            return null;
        }
        CartEntity cart = carts.findActiveByIdAndUserIdForUpdate(request.cartId(), userId)
                .orElseThrow(() -> checkoutChanged("Cart is no longer available."));
        if (session.getCartVersion() == null || session.getCartVersion() != cart.getVersion()) {
            throw checkoutChanged("Cart has changed.");
        }
        return cart;
    }

    private static ApiException checkoutChanged(String message) {
        return new ApiException(ErrorCode.CHECKOUT_CHANGED, HttpStatus.CONFLICT, message);
    }

    private Map<UUID, InventoryBalanceEntity> lockTrackedBalances(CheckoutRevalidation revalidated) {
        if (revalidated.trackedOfferIds().isEmpty()) return Map.of();
        return inventoryLocks.lockTrackedBalances(revalidated.trackedOfferIds())
                .stream().collect(java.util.stream.Collectors.toMap(InventoryBalanceEntity::getLocationVariantId, balance -> balance));
    }

    private void reserveTrackedItems(UUID userId, CheckoutRevalidation revalidated,
            vn.covie.orders.application.CreatedOrder created, Map<UUID, InventoryBalanceEntity> lockedBalances) {
        if (revalidated.trackedOfferIds().isEmpty()) return;
        List<ReservationLine> lines = revalidated.checkout().items().stream()
                .filter(item -> revalidated.trackedOfferIds().contains(item.candidateItem().locationVariantId()))
                .map(item -> new ReservationLine(item.candidateItem().locationVariantId(), created.id(),
                        created.orderItemIdsByLocationVariant().get(item.candidateItem().locationVariantId()),
                        item.candidateItem().quantity()))
                .toList();
        reservations.reserve(lines, lockedBalances, userId);
    }
}
