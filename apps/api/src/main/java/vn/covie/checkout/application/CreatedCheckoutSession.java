package vn.covie.checkout.application; import java.time.Instant; import java.util.UUID; public record CreatedCheckoutSession(UUID id,String checkoutToken,Instant expiresAt) {}
