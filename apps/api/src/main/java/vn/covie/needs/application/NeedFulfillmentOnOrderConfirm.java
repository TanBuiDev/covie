package vn.covie.needs.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.orders.application.OrderConfirmedParticipant;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderSourceType;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;

@Component
public final class NeedFulfillmentOnOrderConfirm implements OrderConfirmedParticipant {
    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final NeedResponseItemRepository responseItems;
    private final OutboxEventWriter outbox;
    private final AppClock clock;

    public NeedFulfillmentOnOrderConfirm(
            NeedRequestRepository needs,
            NeedResponseRepository responses,
            NeedResponseItemRepository responseItems,
            OutboxEventWriter outbox,
            AppClock clock) {
        this.needs = needs;
        this.responses = responses;
        this.responseItems = responseItems;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void onConfirmed(OrderEntity order, UUID confirmingActorUserId) {
        if (order.getSourceType() != OrderSourceType.NEED_REQUEST) {
            return;
        }
        if (order.getSourceNeedRequestId() == null || order.getSourceNeedResponseItemId() == null) {
            throw new IllegalStateException("NEED_REQUEST Order is missing Need source attribution.");
        }

        NeedRequestEntity need = needs.lockById(order.getSourceNeedRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "NEED_REQUEST Order points to a missing NeedRequest: " + order.getSourceNeedRequestId()));

        NeedResponseItemEntity sourceItem = responseItems.findById(order.getSourceNeedResponseItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "NEED_REQUEST Order points to a missing response item: " + order.getSourceNeedResponseItemId()));
        NeedResponseEntity sourceResponse = responses.findById(sourceItem.getNeedResponseId())
                .orElseThrow(() -> new IllegalStateException(
                        "NeedResponseItem points to a missing response: " + sourceItem.getNeedResponseId()));
        if (!sourceResponse.getNeedRequestId().equals(need.getId())) {
            throw new IllegalStateException("Order Need source chain does not resolve to the same NeedRequest.");
        }

        Instant now = clock.now();
        if (need.getRequestStatus() == NeedStatus.ACTIVE && !need.getExpiresAt().isAfter(now)) {
            need.expire(now);
            outbox.append(new OutboxEventIntent(
                    "NEED_EXPIRED",
                    "NEED_REQUEST",
                    need.getId(),
                    Map.of(
                            "needRequestId", need.getId(),
                            "buyerUserId", need.getBuyerUserId())));
            return;
        }

        if (need.getRequestStatus() == NeedStatus.ACTIVE) {
            need.fulfill(order.getId(), now);
            outbox.append(new OutboxEventIntent(
                    "NEED_FULFILLED",
                    "NEED_REQUEST",
                    need.getId(),
                    Map.of(
                            "needRequestId", need.getId(),
                            "fulfilledOrderId", order.getId(),
                            "buyerUserId", need.getBuyerUserId())));
        }
        // FULFILLED, CANCELLED and EXPIRED are deliberately not resurrected.
    }
}
