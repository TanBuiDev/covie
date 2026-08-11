package vn.covie.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;

class JpaOutboxEventWriterTest extends PostgresIntegrationTest {

    @Autowired
    JpaOutboxEventWriter writer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void persistsPendingOutboxEventInsideTheCallingTransaction() {
        UUID aggregateId = UUID.randomUUID();
        UUID eventId = writer.append(new OutboxEventIntent(
                "CATALOG_ITEM_CREATED", "CATALOG_ITEM", aggregateId, Map.of("slug", "iced-coffee")));
        entityManager.flush();

        Map<String, Object> event = jdbc.queryForMap("""
                select event_type, aggregate_type, aggregate_id, status, attempts, payload ->> 'slug' as slug
                from outbox_events where id = ?
                """, eventId);

        assertThat(event).containsEntry("event_type", "CATALOG_ITEM_CREATED")
                .containsEntry("aggregate_type", "CATALOG_ITEM")
                .containsEntry("aggregate_id", aggregateId)
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0)
                .containsEntry("slug", "iced-coffee");
    }
}
