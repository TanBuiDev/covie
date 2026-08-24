package vn.covie.moderation.outbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.notifications.application.NotificationService;
import vn.covie.notifications.model.NotificationType;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.outbox.application.OutboxEventHandler;
import vn.covie.realtime.SseSignal;
import vn.covie.reviews.persistence.ReviewRepository;

@Component
public class ModerationOutboxHandler implements OutboxEventHandler {
    private final ModerationCaseRepository cases;
    private final CatalogItemRepository catalog;
    private final NeedRequestRepository needs;
    private final ReviewRepository reviews;
    private final MerchantMembershipRepository memberships;
    private final NotificationService notifications;

    public ModerationOutboxHandler(ModerationCaseRepository cases, CatalogItemRepository catalog, NeedRequestRepository needs,
            ReviewRepository reviews, MerchantMembershipRepository memberships, NotificationService notifications) {
        this.cases = cases; this.catalog = catalog; this.needs = needs; this.reviews = reviews;
        this.memberships = memberships; this.notifications = notifications;
    }

    @Override public boolean supports(String type) { return "MODERATION_ACTION_APPLIED".equals(type); }

    @Override public List<SseSignal> handle(OutboxEventContext event) {
        var moderationCase = cases.findById(event.aggregateId()).orElseThrow();
        var target = moderationCase.target();
        Map<String, Object> data = Map.of("targetType", target.type().name(), "targetId", target.id().toString());
        return recipients(moderationCase.getTargetType(), target.id()).stream().distinct().map(userId -> {
            var created = notifications.createFromOutbox(event.eventId(), userId, NotificationType.MODERATION_UPDATED,
                    "Moderation update", "A moderation decision affected your content.", target.type().name(), target.id(), data);
            return created.created() ? new SseSignal(userId, "moderation.updated", data) : null;
        }).filter(java.util.Objects::nonNull).toList();
    }

    private List<UUID> recipients(ModerationTargetType type, UUID targetId) {
        return switch (type) {
            case CATALOG_ITEM -> catalog.findById(targetId).map(item -> memberships.findActiveOwnerAndManagerUserIds(item.getMerchantProfileId())).orElse(List.of());
            case NEED_REQUEST -> needs.findById(targetId).map(need -> List.of(need.getBuyerUserId())).orElse(List.of());
            case REVIEW -> reviews.findById(targetId).map(review -> List.of(review.getBuyerUserId())).orElse(List.of());
            case MERCHANT -> memberships.findActiveOwnerAndManagerUserIds(targetId);
            case USER -> List.of(targetId);
        };
    }
}
