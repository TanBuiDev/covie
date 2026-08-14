package vn.covie.merchants.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "merchant_locations")
public class MerchantLocationEntity {
    @Id private UUID id;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "address_text", nullable = false) private String addressText;
    @Column(name = "location_area_id") private UUID locationAreaId;
    @JdbcTypeCode(SqlTypes.GEOGRAPHY) @Column(name = "geo_point", nullable = false, columnDefinition = "geography(Point,4326)") private Point geoPoint;
    @Enumerated(EnumType.STRING) @Column(name = "pin_source", nullable = false, length = 30) private PinSource pinSource;
    @Column(nullable = false, length = 60) private String timezone;
    @Enumerated(EnumType.STRING) @Column(name = "location_status", nullable = false, length = 30) private MerchantLocationStatus locationStatus;
    @Column(name = "pickup_enabled", nullable = false) private boolean pickupEnabled;
    @Column(name = "seller_delivery_enabled", nullable = false) private boolean sellerDeliveryEnabled;
    @Column(name = "service_enabled", nullable = false) private boolean serviceEnabled;
    @Column(name = "minimum_delivery_order") private Long minimumDeliveryOrder;
    @JdbcTypeCode(Types.CHAR) @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version @Column(nullable = false) private long version;

    protected MerchantLocationEntity() {}

    public static MerchantLocationEntity create(UUID id, UUID merchantProfileId, String name, String addressText,
            double latitude, double longitude, Instant now) {
        org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory(
                new org.locationtech.jts.geom.PrecisionModel(), 4326);
        Point point = factory.createPoint(new org.locationtech.jts.geom.Coordinate(longitude, latitude));
        point.setSRID(4326);
        return create(id, merchantProfileId, name, addressText, null, point, PinSource.GPS,
                "Asia/Ho_Chi_Minh", false, false, false, null, "VND", now);
    }

    public static MerchantLocationEntity create(UUID id, UUID merchantProfileId, String name, String addressText,
            UUID locationAreaId, Point geoPoint, PinSource pinSource, String timezone, boolean pickupEnabled,
            boolean sellerDeliveryEnabled, boolean serviceEnabled, Long minimumDeliveryOrder, String currencyCode,
            Instant now) {
        MerchantLocationEntity location = new MerchantLocationEntity();
        location.id = id;
        location.merchantProfileId = merchantProfileId;
        location.apply(name, addressText, locationAreaId, geoPoint, pinSource, timezone, pickupEnabled,
                sellerDeliveryEnabled, serviceEnabled, minimumDeliveryOrder, currencyCode, now);
        location.locationStatus = MerchantLocationStatus.ACTIVE;
        location.createdAt = now;
        return location;
    }

    public void update(String name, String addressText, UUID locationAreaId, Point geoPoint, PinSource pinSource,
            String timezone, boolean pickupEnabled, boolean sellerDeliveryEnabled, boolean serviceEnabled,
            Long minimumDeliveryOrder, String currencyCode, Instant now) {
        apply(name, addressText, locationAreaId, geoPoint, pinSource, timezone, pickupEnabled,
                sellerDeliveryEnabled, serviceEnabled, minimumDeliveryOrder, currencyCode, now);
    }

    private void apply(String name, String addressText, UUID locationAreaId, Point geoPoint, PinSource pinSource,
            String timezone, boolean pickupEnabled, boolean sellerDeliveryEnabled, boolean serviceEnabled,
            Long minimumDeliveryOrder, String currencyCode, Instant now) {
        this.name = name;
        this.addressText = addressText;
        this.locationAreaId = locationAreaId;
        this.geoPoint = geoPoint;
        this.pinSource = pinSource;
        this.timezone = timezone;
        this.pickupEnabled = pickupEnabled;
        this.sellerDeliveryEnabled = sellerDeliveryEnabled;
        this.serviceEnabled = serviceEnabled;
        this.minimumDeliveryOrder = minimumDeliveryOrder;
        this.currencyCode = currencyCode;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        locationStatus = MerchantLocationStatus.DELETED;
        deletedAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public String getName() { return name; }
    public String getAddressText() { return addressText; }
    public UUID getLocationAreaId() { return locationAreaId; }
    public Point getGeoPoint() { return geoPoint; }
    public PinSource getPinSource() { return pinSource; }
    public String getTimezone() { return timezone; }
    public MerchantLocationStatus getLocationStatus() { return locationStatus; }
    public boolean isPickupEnabled() { return pickupEnabled; }
    public boolean isSellerDeliveryEnabled() { return sellerDeliveryEnabled; }
    public boolean isServiceEnabled() { return serviceEnabled; }
    public Long getMinimumDeliveryOrder() { return minimumDeliveryOrder; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
}
