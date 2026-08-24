package vn.covie.needs.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import vn.covie.moderation.model.ContentModerationStatus;

@Entity
@Table(name = "need_requests")
public class NeedRequestEntity {
    @Id private UUID id;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Enumerated(EnumType.STRING) @Column(name = "need_type", nullable = false) private NeedType needType;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(nullable = false, length = 220) private String title;
    @Column(name = "query_text", nullable = false, columnDefinition = "text") private String queryText;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "requested_quantity") private Long requestedQuantity;
    @Column(name = "location_area_id") private UUID locationAreaId;
    @JdbcTypeCode(SqlTypes.GEOGRAPHY) @Column(name = "geo_point", nullable = false, columnDefinition = "geography(Point,4326)") private Point geoPoint;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "location_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode locationSnapshot;
    @Column(name = "search_radius_meters", nullable = false) private int searchRadiusMeters;
    @Enumerated(EnumType.STRING) @Column(name = "request_status", nullable = false) private NeedStatus requestStatus;
    @Enumerated(EnumType.STRING) @Column(name = "moderation_status", nullable = false) private ContentModerationStatus moderationStatus;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "fulfilled_order_id") private UUID fulfilledOrderId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "closed_at") private Instant closedAt;

    protected NeedRequestEntity() {}

    public static NeedRequestEntity create(
            UUID id, UUID buyerUserId, NeedType needType, UUID categoryId, String title, String queryText,
            String description, Long requestedQuantity, UUID locationAreaId, Point geoPoint, JsonNode locationSnapshot,
            int searchRadiusMeters, Instant expiresAt, Instant now) {
        NeedRequestEntity entity = new NeedRequestEntity();
        entity.id = id;
        entity.buyerUserId = buyerUserId;
        entity.needType = needType;
        entity.categoryId = categoryId;
        entity.title = normalizeRequired(title);
        entity.queryText = normalizeRequired(queryText);
        entity.description = normalizeOptional(description);
        entity.requestedQuantity = requestedQuantity;
        entity.locationAreaId = locationAreaId;
        entity.geoPoint = geoPoint;
        entity.locationSnapshot = locationSnapshot;
        entity.searchRadiusMeters = searchRadiusMeters;
        entity.requestStatus = NeedStatus.ACTIVE;
        entity.moderationStatus = ContentModerationStatus.NOT_REQUIRED;
        entity.expiresAt = expiresAt;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void cancel(Instant now) {
        if (requestStatus != NeedStatus.ACTIVE) {
            throw new IllegalStateException("Only active needs can be cancelled.");
        }
        requestStatus = NeedStatus.CANCELLED;
        closedAt = now;
        updatedAt = now;
    }

    public void expire(Instant now) {
        if (requestStatus == NeedStatus.ACTIVE) {
            requestStatus = NeedStatus.EXPIRED;
            closedAt = now;
            updatedAt = now;
        }
    }

    public void requireModeration(Instant now) { moderationStatus = ContentModerationStatus.PENDING; updatedAt = now; }
    public void approveModeration(Instant now) { moderationStatus = ContentModerationStatus.APPROVED; updatedAt = now; }
    public void rejectModeration(Instant now) { moderationStatus = ContentModerationStatus.REJECTED; updatedAt = now; }
    public void hide(Instant now) { moderationStatus = ContentModerationStatus.HIDDEN; updatedAt = now; }
    public void restoreVisibility(Instant now) { moderationStatus = ContentModerationStatus.APPROVED; updatedAt = now; }

    public boolean isActiveAt(Instant now) {
        return requestStatus == NeedStatus.ACTIVE && expiresAt.isAfter(now);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.strip().isEmpty()) throw new IllegalArgumentException("Value is required.");
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public NeedType getNeedType() { return needType; }
    public UUID getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getQueryText() { return queryText; }
    public String getDescription() { return description; }
    public Long getRequestedQuantity() { return requestedQuantity; }
    public UUID getLocationAreaId() { return locationAreaId; }
    public Point getGeoPoint() { return geoPoint; }
    public JsonNode getLocationSnapshot() { return locationSnapshot; }
    public int getSearchRadiusMeters() { return searchRadiusMeters; }
    public NeedStatus getRequestStatus() { return requestStatus; }
    public ContentModerationStatus getModerationStatus() { return moderationStatus; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getFulfilledOrderId() { return fulfilledOrderId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
}
