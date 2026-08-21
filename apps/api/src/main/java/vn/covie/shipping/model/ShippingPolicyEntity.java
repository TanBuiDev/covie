package vn.covie.shipping.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity @Table(name = "shipping_policies")
public class ShippingPolicyEntity {
    @Id private UUID id;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ShippingMode mode;
    @Column(name = "base_fee") private Long baseFee;
    @JdbcTypeCode(Types.CHAR) @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Enumerated(EnumType.STRING) @Column(name = "rule_match_mode", length = 10) private ShippingRuleMatchMode ruleMatchMode;
    @Enumerated(EnumType.STRING) @Column(name = "policy_status", nullable = false, length = 20) private ShippingPolicyStatus policyStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected ShippingPolicyEntity() {}
    public static ShippingPolicyEntity create(UUID id, UUID locationId, ShippingMode mode, Long baseFee, String currency,
            ShippingRuleMatchMode matchMode, ShippingPolicyStatus status, Instant now) {
        ShippingPolicyEntity p = new ShippingPolicyEntity(); p.id=id; p.merchantLocationId=locationId; p.mode=mode; p.baseFee=baseFee; p.currencyCode=currency; p.ruleMatchMode=matchMode; p.policyStatus=status; p.createdAt=now; p.updatedAt=now; return p;
    }
    public void update(ShippingMode mode, Long baseFee, String currency, ShippingRuleMatchMode matchMode, ShippingPolicyStatus status, Instant now) { this.mode=mode; this.baseFee=baseFee; this.currencyCode=currency; this.ruleMatchMode=matchMode; this.policyStatus=status; this.updatedAt=now; }
    public UUID getId(){return id;} public UUID getMerchantLocationId(){return merchantLocationId;} public ShippingMode getMode(){return mode;} public Long getBaseFee(){return baseFee;} public String getCurrencyCode(){return currencyCode;} public ShippingRuleMatchMode getRuleMatchMode(){return ruleMatchMode;} public ShippingPolicyStatus getPolicyStatus(){return policyStatus;} public long getVersion(){return version;}
}
