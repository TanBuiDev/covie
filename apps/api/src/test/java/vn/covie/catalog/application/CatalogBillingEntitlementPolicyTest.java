package vn.covie.catalog.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.covie.billing.application.BillingEntitlementService;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.policy.CatalogPublishPolicy;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.moderation.ModerationCaseWriter;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class CatalogBillingEntitlementPolicyTest {
    @Test void deniesPublishBeforeMutatingCatalogWhenEntitlementMissing() {
        UUID merchantId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        BillingEntitlementService billing = mock(BillingEntitlementService.class);
        doThrow(new ApiException(ErrorCode.BILLING_SUBSCRIPTION_REQUIRED,
            HttpStatus.CONFLICT, "Subscription required."))
            .when(billing).requireCanSell(merchantId);
        CatalogLifecycleService service = new CatalogLifecycleService(access,
            mock(CatalogItemRepository.class), mock(CatalogManagementActionPolicy.class),
            mock(CatalogPublishPolicy.class), mock(OutboxEventWriter.class),
            mock(ModerationCaseWriter.class), () -> java.time.Instant.EPOCH, billing);

        assertThrows(ApiException.class,
            () -> service.publish(merchantId, UUID.randomUUID(), "\"v1\""));
        verify(access).require(merchantId, MerchantCapability.PUBLISH_CATALOG);
        verify(billing).requireCanSell(merchantId);
    }

    @Test void deniesReactivationBeforeMutatingCatalogWhenEntitlementMissing() {
        UUID merchantId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        BillingEntitlementService billing = mock(BillingEntitlementService.class);
        doThrow(new ApiException(ErrorCode.BILLING_SUBSCRIPTION_REQUIRED,
            HttpStatus.CONFLICT, "Subscription required."))
            .when(billing).requireCanSell(merchantId);
        CatalogLifecycleService service = new CatalogLifecycleService(access,
            mock(CatalogItemRepository.class), mock(CatalogManagementActionPolicy.class),
            mock(CatalogPublishPolicy.class), mock(OutboxEventWriter.class),
            mock(ModerationCaseWriter.class), () -> java.time.Instant.EPOCH, billing);

        assertThrows(ApiException.class,
            () -> service.activate(merchantId, UUID.randomUUID(), "\"v1\""));
        verify(billing).requireCanSell(merchantId);
    }
}
