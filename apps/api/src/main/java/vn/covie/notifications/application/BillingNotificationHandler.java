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
        "BILLING_RENEWAL_REMINDER","SELLER_SUBSCRIPTION_PAYMENT_SETTLED",
        "BILLING_SUBSCRIPTION_EXPIRED", "BILLING_PRICE_PUBLISHED");
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public BillingNotificationHandler(JdbcTemplate jdbc,NotificationService notifications){
        this.jdbc=jdbc;this.notifications=notifications;
    }

    @Override public boolean supports(String eventType){return EVENTS.contains(eventType);}

    @Override public List<SseSignal> handle(OutboxEventContext event){
        if ("BILLING_PRICE_PUBLISHED".equals(event.eventType())) return pricePublished(event);
        UUID merchantId;
        try {
            merchantId = UUID.fromString(event.payload().path("merchantId").asText());
        } catch (IllegalArgumentException ex) { throw new IllegalStateException("Missing merchant ID",ex); }
        boolean reminder="BILLING_RENEWAL_REMINDER".equals(event.eventType());
        int days=event.payload().path("daysBefore").asInt(0);
        boolean expired="BILLING_SUBSCRIPTION_EXPIRED".equals(event.eventType());
        NotificationType type=expired?NotificationType.BILLING_SUBSCRIPTION_EXPIRED:
            (reminder ? NotificationType.BILLING_RENEWAL_REMINDER : NotificationType.BILLING_PAYMENT_RECEIVED);
        String title=expired?"Thuê bao cửa hàng đã hết hạn":
            (reminder ? "Sắp hết hạn thuê bao cửa hàng" : "Thanh toán thuê bao thành công");
        String body=expired?"Vui lòng gia hạn để khôi phục quyền đăng bán.":
            (reminder ? "Thuê bao cửa hàng còn "+days+" ngày. Bạn có thể gia hạn tại Seller Center."
                : "CoVie đã xác minh và ghi nhận thanh toán thuê bao.");
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

    private List<SseSignal> pricePublished(OutboxEventContext event) {
        var price = jdbc.queryForObject("""
            select plan_code, revision, price_vnd, cycle_months, effective_at
            from billing_plan_versions where id=?
            """, (row, index) -> new PriceNotice(row.getString("plan_code"), row.getInt("revision"),
                row.getLong("price_vnd"), row.getInt("cycle_months"),
                row.getTimestamp("effective_at").toInstant()), event.aggregateId());
        if (price == null) throw new IllegalStateException("Missing published price version");
        Map<String, Object> payload = Map.of("noticeKind", "PRICE_CHANGED", "planVersionId", event.aggregateId().toString(),
            "planCode", price.planCode(), "revision", price.revision(), "priceVnd", price.priceVnd(),
            "cycleMonths", price.cycleMonths(), "effectiveAt", price.effectiveAt().toString(), "deepLink", "/seller");
        var result = new ArrayList<SseSignal>();
        UUID after = null;
        while (true) {
            var owners = jdbc.query("""
                select distinct membership.user_id from merchant_memberships membership
                join merchant_subscriptions subscription on subscription.merchant_id=membership.merchant_profile_id
                where subscription.plan_code=? and membership.role='OWNER' and membership.membership_status='ACTIVE'
                  and (?::uuid is null or membership.user_id>?)
                order by membership.user_id limit 100
                """, (row, index) -> (UUID) row.getObject(1), price.planCode(), after, after);
            if (owners.isEmpty()) break;
            for (UUID owner : owners) {
                var created = notifications.createFromOutbox(event.eventId(), owner, NotificationType.BILLING_RENEWAL_REMINDER,
                    "Bảng giá thuê bao được cập nhật", "Xem giá và lịch hiệu lực mới. Kỳ đã thanh toán không thay đổi; không tự động thu phí.",
                    "BILLING_PLAN_VERSION", event.aggregateId(), payload);
                if (created.created()) result.add(new SseSignal(owner, "notification.created",
                    Map.of("notificationId", created.notificationId().toString())));
            }
            after = owners.getLast();
        }
        return result;
    }

    private record PriceNotice(String planCode, int revision, long priceVnd, int cycleMonths,
                               java.time.Instant effectiveAt) {}
}
