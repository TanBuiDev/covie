package vn.covie.checkout.application;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.billing.application.BillingEntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public class CheckoutTradingPolicy {
    private final MerchantProfileRepository merchants;
    private final BillingEntitlementService entitlements;

    /** Kept for existing narrow unit tests; Spring always uses the wired constructor below. */
    public CheckoutTradingPolicy(MerchantProfileRepository merchants) {
        this.merchants = merchants;
        this.entitlements = null;
    }

    @Autowired
    public CheckoutTradingPolicy(MerchantProfileRepository merchants, BillingEntitlementService entitlements) {
        this.merchants = merchants;
        this.entitlements = entitlements;
    }

    public void requireTrading(UUID merchantId, MerchantLocationEntity location) {
        if (!locationActive(location) || !merchantId.equals(location.getMerchantProfileId()))
            throw new ApiException(ErrorCode.CHECKOUT_LOCATION_UNAVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Location unavailable.");
        if (!merchantVisible(merchants.findByIdAndDeletedAtIsNull(merchantId).orElse(null)))
            throw new ApiException(ErrorCode.OFFER_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Offer unavailable.");
        // Called in checkout preview and again within commit revalidation. Never trust a browser flag.
        if (entitlements != null) entitlements.requireCanSell(merchantId);
    }

    public static boolean locationActive(MerchantLocationEntity location) {
        return location != null && location.getDeletedAt() == null && location.getLocationStatus() == MerchantLocationStatus.ACTIVE;
    }

    public static boolean merchantVisible(MerchantProfileEntity merchant) {
        return merchant != null && merchant.getDeletedAt() == null && merchant.getMerchantStatus() == MerchantStatus.ACTIVE
                && (merchant.getModerationStatus() == MerchantModerationStatus.NOT_REQUIRED
                    || merchant.getModerationStatus() == MerchantModerationStatus.APPROVED);
    }
}
