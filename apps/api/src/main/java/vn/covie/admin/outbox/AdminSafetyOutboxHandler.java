package vn.covie.admin.outbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.notifications.application.NotificationService;
import vn.covie.notifications.model.NotificationType;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.outbox.application.OutboxEventHandler;
import vn.covie.realtime.SseSignal;

@Component
public class AdminSafetyOutboxHandler implements OutboxEventHandler {
    private final MerchantMembershipRepository memberships;
    private final NotificationService notifications;

    public AdminSafetyOutboxHandler(MerchantMembershipRepository memberships, NotificationService notifications) {
        this.memberships = memberships;
        this.notifications = notifications;
    }

    @Override public boolean supports(String type) {
        return switch (type) {
            case "ADMIN_USER_SUSPENDED", "ADMIN_USER_RESTORED", "ADMIN_MERCHANT_SUSPENDED", "ADMIN_MERCHANT_RESTORED" -> true;
            default -> false;
        };
    }

    @Override public List<SseSignal> handle(OutboxEventContext event) {
        return switch (event.eventType()) {
            case "ADMIN_USER_SUSPENDED" -> List.of(); // An inactive account cannot consume a persisted notification.
            case "ADMIN_USER_RESTORED" -> notifyUser(event, event.aggregateId(), NotificationType.ACCOUNT_RESTORED,
                    "Account restored", "Your account has been restored.", "user.updated");
            case "ADMIN_MERCHANT_SUSPENDED" -> notifyMerchant(event, NotificationType.MERCHANT_SUSPENDED,
                    "Merchant suspended", "Your merchant account has been suspended.");
            case "ADMIN_MERCHANT_RESTORED" -> notifyMerchant(event, NotificationType.MERCHANT_RESTORED,
                    "Merchant restored", "Your merchant account has been restored.");
            default -> List.of();
        };
    }

    private List<SseSignal> notifyUser(OutboxEventContext event, UUID userId, NotificationType type, String title, String body, String signal) {
        Map<String, Object> data = Map.of("userId", userId.toString());
        var created = notifications.createFromOutbox(event.eventId(), userId, type, title, body, "USER", userId, data);
        return created.created() ? List.of(new SseSignal(userId, signal, data)) : List.of();
    }

    private List<SseSignal> notifyMerchant(OutboxEventContext event, NotificationType type, String title, String body) {
        UUID merchantId = event.aggregateId();
        Map<String, Object> data = Map.of("merchantId", merchantId.toString());
        return memberships.findActiveOwnerAndManagerUserIds(merchantId).stream().map(userId -> {
            var created = notifications.createFromOutbox(event.eventId(), userId, type, title, body, "MERCHANT", merchantId, data);
            return created.created() ? new SseSignal(userId, "merchant.updated", data) : null;
        }).filter(java.util.Objects::nonNull).toList();
    }
}
