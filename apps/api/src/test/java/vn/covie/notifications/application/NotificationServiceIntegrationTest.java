package vn.covie.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.notifications.model.NotificationType;
import vn.covie.notifications.persistence.NotificationDeliveryRepository;
import vn.covie.notifications.persistence.NotificationRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.outbox.OutboxEventRepository;

class NotificationServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OutboxEventWriter outbox;
    @Autowired OutboxEventRepository outboxEvents;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired vn.covie.needs.application.NeedScheduledTransitionService expiration;
    @Autowired vn.covie.outbox.application.OutboxProcessor processor;

    @Test
    void committedNeedExpirationDispatchesOnePrivateBuyerNotification() {
        UUID buyer = UUID.randomUUID(), category = UUID.randomUUID(), need = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(buyer, "Need expiration buyer", now));
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order) values (?,'Need test',?,'PRODUCT','ACTIVE',0)", category, "need-dispatch-" + category);
        jdbc.update("insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,geo_point,location_snapshot,search_radius_meters,request_status,expires_at,created_at,updated_at) values (?,?,'PRODUCT',?,'Need','query',ST_SetSRID(ST_MakePoint(106.7,10.8),4326)::geography,'{}'::jsonb,5000,'ACTIVE',?,?,?)",
                need, buyer, category, java.sql.Timestamp.from(now.minusSeconds(60)), java.sql.Timestamp.from(now.minusSeconds(3600)), java.sql.Timestamp.from(now.minusSeconds(3600)));
        transactions.executeWithoutResult(status -> expiration.expireDueBatch(50));
        UUID eventId = jdbc.queryForObject("select id from outbox_events where aggregate_id=? and event_type='NEED_EXPIRED'", UUID.class, need);
        for (int attempt = 0; attempt < 100; attempt++) {
            if (outboxEvents.findById(eventId).orElseThrow().getStatus() == vn.covie.shared.outbox.OutboxEventStatus.PUBLISHED) break;
            processor.processOne();
        }
        assertThat(outboxEvents.findById(eventId).orElseThrow().getStatus()).isEqualTo(vn.covie.shared.outbox.OutboxEventStatus.PUBLISHED);
        var notification = notifications.findBySourceOutboxEventIdAndUserIdAndNotificationType(eventId, buyer, NotificationType.NEED_EXPIRED).orElseThrow();
        assertThat(notification.getPayload().get("needRequestId").asText()).isEqualTo(need.toString());
        assertThat(notification.getPayload().toString()).doesNotContain("latitude", "longitude", "address", "geo");
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?", Long.class, eventId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from notification_deliveries where notification_id=?", Long.class, notification.getId())).isEqualTo(1L);
    }

    @Test @Transactional
    void retryForSameNeedEventCreatesOneNotificationAndOneInAppDeliveryWithoutBuyerLocation() {
        UUID userId=UUID.randomUUID(), needId=UUID.randomUUID(), locationId=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId, "notification-test", Instant.now()));
        UUID unrelatedNeedId=UUID.randomUUID();
        UUID unrelatedEventId=outbox.append(new OutboxEventIntent("NEED_CREATED", "NEED_REQUEST", unrelatedNeedId, Map.of()));
        outboxEvents.flush();
        service.createFromOutbox(unrelatedEventId,userId,NotificationType.NEED_MATCH_AVAILABLE,"Other match","Other request.","NEED_REQUEST",unrelatedNeedId,Map.of());
        UUID eventId=outbox.append(new OutboxEventIntent("NEED_CREATED", "NEED_REQUEST", needId, Map.of()));
        outboxEvents.flush();
        Map<String,Object> payload=Map.of("needRequestId",needId.toString(),"merchantLocationId",locationId.toString());
        var first=service.createFromOutbox(eventId,userId,NotificationType.NEED_MATCH_AVAILABLE,"Matching need available","A nearby request may match your offer.","NEED_REQUEST",needId,payload);
        var retried=service.createFromOutbox(eventId,userId,NotificationType.NEED_MATCH_AVAILABLE,"Matching need available","A nearby request may match your offer.","NEED_REQUEST",needId,payload);
        assertThat(first.created()).isTrue(); assertThat(retried.created()).isFalse(); assertThat(retried.notificationId()).isEqualTo(first.notificationId());
        var stored=notifications.findById(first.notificationId()).orElseThrow();
        assertThat(stored.getPayload().toString()).doesNotContain("latitude", "longitude", "address", "geo");
        deliveries.flush();
        assertThat(jdbc.queryForObject("select count(*) from notification_deliveries where notification_id = ? and channel = 'IN_APP'", Long.class, first.notificationId())).isEqualTo(1L);
    }
}
