package vn.covie.needs.persistence;

import java.util.UUID;

public record NeedEligibleCandidateRow(
        UUID catalogItemId, UUID variantId, UUID locationVariantId,
        String catalogItemName, String variantName) {
    public NeedEligibleCandidateKey key() {
        return new NeedEligibleCandidateKey(catalogItemId, variantId, locationVariantId);
    }
}
