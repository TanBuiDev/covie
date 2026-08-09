package vn.covie.users.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@Entity
@Table(name = "user_addresses")
public class UserAddressEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 80) private String label;
    @Column(name = "recipient_name", length = 120) private String recipientName;
    @Column(length = 30) private String phone;
    @Column(name = "address_text", nullable = false) private String addressText;
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "geo_point", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point geoPoint;
    @Enumerated(EnumType.STRING)
    @Column(name = "pin_source", nullable = false, length = 30)
    private PinSource pinSource;
    @Column private String note;
    @Column(name = "is_default", nullable = false) private boolean defaultAddress;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;
    protected UserAddressEntity() {}
    public static Point point(double longitude, double latitude) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        Point point = factory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }

    public static UserAddressEntity create(UUID id, UUID userId, String label, String recipientName, String phone,
            String addressText, double latitude, double longitude, PinSource pinSource, String note,
            boolean defaultAddress, Instant now) {
        UserAddressEntity address = new UserAddressEntity();
        address.id = id; address.userId = userId; address.label = label; address.recipientName = recipientName;
        address.phone = phone; address.addressText = addressText; address.geoPoint = point(longitude, latitude);
        address.pinSource = pinSource; address.note = note; address.defaultAddress = defaultAddress;
        address.status = "ACTIVE"; address.createdAt = now; address.updatedAt = now;
        return address;
    }

    public void update(String label, String recipientName, String phone, String addressText, double latitude,
            double longitude, PinSource pinSource, String note, Instant now) {
        this.label = label; this.recipientName = recipientName; this.phone = phone; this.addressText = addressText;
        this.geoPoint = point(longitude, latitude); this.pinSource = pinSource; this.note = note; this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressText() { return addressText; }
    public Point getGeoPoint() { return geoPoint; }
    public PinSource getPinSource() { return pinSource; }
    public String getNote() { return note; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setDefaultAddress(boolean value) { defaultAddress = value; }
    public void delete(Instant now) { status = "DELETED"; defaultAddress = false; deletedAt = now; updatedAt = now; }
}
