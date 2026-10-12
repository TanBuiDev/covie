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
    public static OrderIssueEntity open(UUID id, UUID orderId, OrderIssueType type, UUID reporter, String reason,
            Instant now, Instant closeAfterAt) {
        OrderIssueEntity issue=new OrderIssueEntity(); issue.id=id;issue.orderId=orderId;issue.issueType=type;
        issue.reportedByUserId=reporter;issue.reasonText=reason;issue.issueStatus=OrderIssueStatus.OPEN;
        issue.openedAt=now;issue.closeAfterAt=closeAfterAt;return issue;
    }
    public void resolveByParties(Instant now){if(issueStatus==OrderIssueStatus.OPEN){issueStatus=OrderIssueStatus.RESOLVED_BY_PARTIES;resolvedAt=now;}}
    public void closeByTimeout(Instant now){if(issueStatus==OrderIssueStatus.OPEN){issueStatus=OrderIssueStatus.CLOSED_BY_TIMEOUT;closedAt=now;}}
    public UUID getId(){return id;} public UUID getOrderId(){return orderId;} public OrderIssueType getIssueType(){return issueType;}
    public OrderIssueStatus getIssueStatus(){return issueStatus;} public Instant getCloseAfterAt(){return closeAfterAt;}
    public UUID getReportedByUserId(){return reportedByUserId;} public String getReasonText(){return reasonText;}
    public Instant getOpenedAt(){return openedAt;} public Instant getResolvedAt(){return resolvedAt;} public Instant getClosedAt(){return closedAt;}
}
