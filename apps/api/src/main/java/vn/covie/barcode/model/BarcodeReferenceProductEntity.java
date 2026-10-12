package vn.covie.barcode.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "barcode_reference_products")
public class BarcodeReferenceProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String gtin;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "gtin14", insertable = false, updatable = false, length = 14)
    private String gtin14;

    @Column(name = "product_name", nullable = false, length = 220)
    private String productName;

    @Column(length = 160)
    private String brand;

    @Column(length = 80)
    private String quantity;

    @Column(name = "category_label", length = 200)
    private String categoryLabel;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "market_country", nullable = false, length = 2)
    private String marketCountry;

    @Column(name = "market_signal", nullable = false, length = 40)
    private String marketSignal;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_record_url", nullable = false)
    private String sourceRecordUrl;

    @Column(name = "evidence_url", nullable = false)
    private String evidenceUrl;

    @Column(name = "license_id", nullable = false, length = 40)
    private String licenseId;

    @Column(name = "quality_status", nullable = false, length = 40)
    private String qualityStatus;

    @Column(name = "source_modified_at")
    private Instant sourceModifiedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected BarcodeReferenceProductEntity() {}

    public static BarcodeReferenceProductEntity of(
        Long id, String gtin, String gtin14, String productName, String brand,
        String quantity, String categoryLabel, String marketCountry, String marketSignal,
        String source, String sourceRecordUrl, String evidenceUrl, String licenseId,
        String qualityStatus, boolean active
    ) {
        BarcodeReferenceProductEntity entity = new BarcodeReferenceProductEntity();
        entity.id = id;
        entity.gtin = gtin;
        entity.gtin14 = gtin14;
        entity.productName = productName;
        entity.brand = brand;
        entity.quantity = quantity;
        entity.categoryLabel = categoryLabel;
        entity.marketCountry = marketCountry;
        entity.marketSignal = marketSignal;
        entity.source = source;
        entity.sourceRecordUrl = sourceRecordUrl;
        entity.evidenceUrl = evidenceUrl;
        entity.licenseId = licenseId;
        entity.qualityStatus = qualityStatus;
        entity.active = active;
        entity.firstSeenAt = Instant.now();
        entity.lastSeenAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getGtin() {
        return gtin;
    }

    public String getGtin14() {
        return gtin14;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrand() {
        return brand;
    }

    public String getQuantity() {
        return quantity;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public String getMarketCountry() {
        return marketCountry;
    }

    public String getMarketSignal() {
        return marketSignal;
    }

    public String getSource() {
        return source;
    }

    public String getSourceRecordUrl() {
        return sourceRecordUrl;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public Instant getSourceModifiedAt() {
        return sourceModifiedAt;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isActive() {
        return active;
    }
}
