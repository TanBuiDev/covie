package vn.covie.checkout.application; import java.util.UUID; public record CheckoutCandidateItem(UUID locationVariantId,UUID variantId,UUID catalogItemId,long quantity) {}
