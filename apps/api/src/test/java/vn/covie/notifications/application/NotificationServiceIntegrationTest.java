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
