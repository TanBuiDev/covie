package vn.covie.notifications.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.sql.Timestamp;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.notifications.model.NotificationEntity;
import vn.covie.notifications.model.NotificationType;
import vn.covie.support.PostgresIntegrationTest;

class NotificationRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    @Test
    @Transactional
    void supportedDemoTypesRetainLinksAndPaginateWithinTheOwnerInbox() {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID(), order = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-06T00:00:00Z");
        for (UUID user : List.of(owner, other)) {
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Test','ACTIVE',?,?)",
                    user, Timestamp.from(now), Timestamp.from(now));
        }
        var types = List.of(NotificationType.ORDER_CANCELLED, NotificationType.ORDER_NEW,
                NotificationType.ORDER_CONFIRMED, NotificationType.ORDER_FULFILLMENT_UPDATED,
                NotificationType.ORDER_COMPLETED);
        for (int index=0; index<types.size(); index++) {
            UUID id = new UUID(0, index+1);
            notifications.saveAndFlush(NotificationEntity.create(id, owner, types.get(index), "Demo", "Update",
                    "ORDER", order, JsonNodeFactory.instance.objectNode().put("orderId",order.toString())
                            .put("deepLink","/orders/"+order), null, now));
        }
        notifications.saveAndFlush(NotificationEntity.create(UUID.randomUUID(),other,NotificationType.ORDER_COMPLETED,
                "Other", "Other", "ORDER",order,JsonNodeFactory.instance.objectNode(),null,now.plusSeconds(1)));
        entities.clear();
        var first = notifications.findByUserIdOrderByCreatedAtDescIdDesc(owner,PageRequest.of(0,2));
        assertThat(first).extracting(NotificationEntity::getNotificationType)
                .containsExactly(NotificationType.ORDER_COMPLETED, NotificationType.ORDER_FULFILLMENT_UPDATED);
        assertThat(first).allSatisfy(n -> {
            assertThat(n.getPayload().path("deepLink").asText()).isEqualTo("/orders/"+order);
            assertThat(n.getReadAt()).isNull();
        });
        var second = notifications.pageAfter(owner,now,first.getLast().getId(),PageRequest.of(0,2));
        assertThat(second).extracting(NotificationEntity::getNotificationType)
                .containsExactly(NotificationType.ORDER_CONFIRMED,NotificationType.ORDER_NEW);
        var last = notifications.pageAfter(owner,now,second.getLast().getId(),PageRequest.of(0,2));
        assertThat(last).extracting(NotificationEntity::getNotificationType).containsExactly(NotificationType.ORDER_CANCELLED);
        assertThat(notifications.pageAfter(owner,now,last.getLast().getId(),PageRequest.of(0,2))).isEmpty();
        assertThat(notifications.markRead(first.getFirst().getId(),other,now)).isZero();
        assertThat(notifications.markRead(first.getFirst().getId(),owner,now)).isEqualTo(1);
        entities.clear();
        assertThat(notifications.findByIdAndUserId(first.getFirst().getId(),owner).orElseThrow().getReadAt()).isEqualTo(now);
    }

    @Test
    void loadsTheFirstInboxPageWithoutAnUntypedNullCursor() {
        UUID userId = UUID.randomUUID();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 21))).isEmpty();
        assertThat(notifications.pageAfter(userId, Instant.now(), UUID.randomUUID(), PageRequest.of(0, 21))).isEmpty();
    }
}
