package vn.covie.checkout.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.covie.billing.application.BillingEntitlementService;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class CheckoutBillingEntitlementPolicyTest {
    @Test
    void deniesNewBuyerCheckoutWhenSellerEntitlementIsMissing() {
        UUID merchantId = UUID.randomUUID();
        MerchantProfileRepository merchants = mock(MerchantProfileRepository.class);
        BillingEntitlementService billing = mock(BillingEntitlementService.class);
        var profile = MerchantProfileEntity.create(merchantId, "Seller", "seller", Instant.EPOCH);
        var location = MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Branch",
            "Address", 10.75, 106.67, Instant.EPOCH);
        when(merchants.findByIdAndDeletedAtIsNull(merchantId)).thenReturn(Optional.of(profile));
        doThrow(new ApiException(ErrorCode.BILLING_SUBSCRIPTION_REQUIRED,
            HttpStatus.CONFLICT, "Subscription required."))
            .when(billing).requireCanSell(merchantId);

        assertThrows(ApiException.class,
            () -> new CheckoutTradingPolicy(merchants, billing).requireTrading(merchantId, location));
        verify(billing).requireCanSell(merchantId);
    }

    @Test
    void letsBuyerCheckoutContinueWhenSellerEntitlementIsValid() {
        UUID merchantId = UUID.randomUUID();
        MerchantProfileRepository merchants = mock(MerchantProfileRepository.class);
        BillingEntitlementService billing = mock(BillingEntitlementService.class);
        when(merchants.findByIdAndDeletedAtIsNull(merchantId))
            .thenReturn(Optional.of(MerchantProfileEntity.create(merchantId, "Seller", "seller", Instant.EPOCH)));
        var location = MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Branch",
            "Address", 10.75, 106.67, Instant.EPOCH);

        new CheckoutTradingPolicy(merchants, billing).requireTrading(merchantId, location);
        verify(billing).requireCanSell(merchantId);
    }
}
