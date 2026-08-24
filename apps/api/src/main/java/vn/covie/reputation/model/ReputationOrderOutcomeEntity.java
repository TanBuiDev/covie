package vn.covie.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reputation_order_outcomes")
public class ReputationOrderOutcomeEntity {
    @Id @Column(name = "order_id") private UUID orderId;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Enumerated(EnumType.STRING) @Column(name = "buyer_outcome", nullable = false) private BuyerReputationOutcome buyerOutcome;
    @Enumerated(EnumType.STRING) @Column(name = "merchant_outcome", nullable = false) private MerchantReputationOutcome merchantOutcome;
    @Column(name = "source_order_status", nullable = false) private String sourceOrderStatus;
    @Column(name = "source_outbox_event_id") private UUID sourceOutboxEventId;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected ReputationOrderOutcomeEntity() {}
    public static ReputationOrderOutcomeEntity create(UUID orderId, UUID buyerUserId, UUID merchantProfileId,
            BuyerReputationOutcome buyerOutcome, MerchantReputationOutcome merchantOutcome, String sourceOrderStatus,
            UUID sourceOutboxEventId, Instant now) {
        ReputationOrderOutcomeEntity outcome = new ReputationOrderOutcomeEntity();
        outcome.orderId=orderId; outcome.buyerUserId=buyerUserId; outcome.merchantProfileId=merchantProfileId;
        outcome.buyerOutcome=buyerOutcome; outcome.merchantOutcome=merchantOutcome; outcome.sourceOrderStatus=sourceOrderStatus;
        outcome.sourceOutboxEventId=sourceOutboxEventId; outcome.updatedAt=now; return outcome;
    }
    public void replace(BuyerReputationOutcome buyerOutcome, MerchantReputationOutcome merchantOutcome, String sourceOrderStatus, UUID sourceOutboxEventId, Instant now) { this.buyerOutcome=buyerOutcome; this.merchantOutcome=merchantOutcome; this.sourceOrderStatus=sourceOrderStatus; this.sourceOutboxEventId=sourceOutboxEventId; this.updatedAt=now; }
    public UUID getOrderId() { return orderId; } public UUID getBuyerUserId() { return buyerUserId; } public UUID getMerchantProfileId() { return merchantProfileId; }
    public BuyerReputationOutcome getBuyerOutcome() { return buyerOutcome; } public MerchantReputationOutcome getMerchantOutcome() { return merchantOutcome; }
}
