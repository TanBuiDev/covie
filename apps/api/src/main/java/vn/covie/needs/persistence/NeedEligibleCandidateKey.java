package vn.covie.needs.persistence;

import java.util.UUID;

public record NeedEligibleCandidateKey(UUID catalogItemId, UUID variantId, UUID locationVariantId) {}
