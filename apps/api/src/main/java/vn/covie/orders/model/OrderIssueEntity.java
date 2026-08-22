package vn.covie.orders.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_issues")
public class OrderIssueEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(name = "issue_type", nullable = false) private OrderIssueType issueType;
    @Column(name = "reported_by_user_id", nullable = false) private UUID reportedByUserId;
    @Column(name = "reason_text") private String reasonText;
    @Enumerated(EnumType.STRING) @Column(name = "issue_status", nullable = false) private OrderIssueStatus issueStatus;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "close_after_at", nullable = false) private Instant closeAfterAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    protected OrderIssueEntity() {}
}
