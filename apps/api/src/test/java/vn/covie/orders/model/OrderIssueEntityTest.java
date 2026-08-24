package vn.covie.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderIssueEntityTest {
    @Test
    void opensIssueWithAStableCloseDeadline() {
        Instant now=Instant.parse("2026-08-22T00:00:00Z");
        OrderIssueEntity issue=OrderIssueEntity.open(UUID.randomUUID(),UUID.randomUUID(),OrderIssueType.DELIVERY_NOT_RECEIVED,
                UUID.randomUUID(),"Missing",now,now.plusSeconds(172800));
        assertThat(issue.getIssueStatus()).isEqualTo(OrderIssueStatus.OPEN);
        assertThat(issue.getCloseAfterAt()).isEqualTo(now.plusSeconds(172800));
    }
}
