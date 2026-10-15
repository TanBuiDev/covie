package vn.covie.notifications.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.covie.notifications.model.NotificationType;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.outbox.application.OutboxEventHandler;
import vn.covie.realtime.SseSignal;

/** Billing notifications go to merchant OWNERs, never buyer-only accounts. */
@Component
public class BillingNotificationHandler implements OutboxEventHandler {
    private static final Set<String> EVENTS = Set.of(
        "BILLING_RENEWAL_REMINDER","SELLER_SUBSCRIPTION_PAYMENT_SETTLED");
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public BillingNotificationHandler(JdbcTemplate jdbc,NotificationService notifications){
        this.jdbc=jdbc;this.notifications=notifications;
    }

    @Override public boolean supports(String eventType){return EVENTS.contains(eventType);}

    @Override public List<SseSignal> handle(OutboxEventContext event){
        UUID merchantId;
        try {
            merchantId = UUID.fromString(event.payload().path("merchantId").asText());
        } catch (IllegalArgumentException ex) { throw new IllegalStateException("Missing merchant ID",ex); }
        boolean reminder="BILLING_RENEWAL_REMINDER".equals(event.eventType());
        int days=event.payload().path("daysBefore").asInt(0);
        NotificationType type=reminder ? NotificationType.BILLING_RENEWAL_REMINDER
            : NotificationType.BILLING_PAYMENT_RECEIVED;
        String title=reminder ? "Sắp hết hạn thuê bao cửa hàng" : "Thanh toán thuê bao thành công";
        String body=reminder ? "Thuê bao cửa hàng còn "+days+" ngày. Bạn có thể gia hạn tại Seller Center."
            : "CoVie đã xác minh và ghi nhận thanh toán thuê bao.";
        var owners=jdbc.query("""
            select distinct user_id from merchant_memberships
            where merchant_profile_id=? and role='OWNER' and membership_status='ACTIVE'
            """,(rs,n)->(UUID)rs.getObject(1),merchantId);
        var result=new ArrayList<SseSignal>();
        for(UUID owner:owners){
            var created=notifications.createFromOutbox(event.eventId(),owner,type,title,body,
                "MERCHANT",merchantId,Map.of("merchantId",merchantId.toString(),
                    "billingUrl","/seller/"+merchantId+"/billing"));
            if(created.created()) result.add(new SseSignal(owner,"notification.created",
                Map.of("notificationId",created.notificationId().toString())));
        }
        return result;
    }
}
