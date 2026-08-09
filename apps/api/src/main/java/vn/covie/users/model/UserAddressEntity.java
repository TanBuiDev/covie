package vn.covie.users.model;

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
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "geo_point", nullable = false, columnDefinition = "geography(Point,4326)") private Point geoPoint;
    @Column(name = "pin_source", nullable = false, length = 30) private String pinSource;
    @Column(name = "is_default", nullable = false) private boolean defaultAddress;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected UserAddressEntity() {}
    public static Point point(double longitude, double latitude) { GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326); Point point = factory.createPoint(new Coordinate(longitude, latitude)); point.setSRID(4326); return point; }
}
