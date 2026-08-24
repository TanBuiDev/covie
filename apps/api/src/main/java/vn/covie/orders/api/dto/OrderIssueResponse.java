package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.OrderIssueStatus;
import vn.covie.orders.model.OrderIssueType;

public record OrderIssueResponse(UUID id, OrderIssueType type, UUID reportedByUserId, String reasonText,
        OrderIssueStatus status, Instant openedAt, Instant closeAfterAt, Instant resolvedAt, Instant closedAt) {}
