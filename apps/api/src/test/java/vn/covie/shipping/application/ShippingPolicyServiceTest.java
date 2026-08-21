package vn.covie.shipping.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shipping.api.dto.ShippingPolicyUpsertRequest;
import vn.covie.shipping.api.dto.ShippingRuleRequest;
import vn.covie.shipping.model.ShippingActionType;
import vn.covie.shipping.model.ShippingConditionOperator;
import vn.covie.shipping.model.ShippingConditionType;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.model.ShippingRuleMatchMode;
import vn.covie.shipping.persistence.ShippingPolicyRepository;
import vn.covie.shipping.persistence.ShippingRuleRepository;

class ShippingPolicyServiceTest {
    @Test
    void rejectsConditionalPolicyWhenEveryRuleIsDisabled() {
        UUID merchantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        MerchantLocationRepository locations = mock(MerchantLocationRepository.class);
        when(locations.findActiveForUpdate(locationId, merchantId)).thenReturn(Optional.of(
                MerchantLocationEntity.create(locationId, merchantId, "Store", "Address", 10, 106,
                        Instant.parse("2026-08-22T00:00:00Z"))));
        ShippingPolicyService service = new ShippingPolicyService(mock(MerchantAccessPolicy.class), locations,
                mock(ShippingPolicyRepository.class), mock(ShippingRuleRepository.class), new UuidV7Generator(),
                () -> Instant.parse("2026-08-22T00:00:00Z"));
        ShippingRuleRequest disabledRule = new ShippingRuleRequest(ShippingConditionType.ORDER_AMOUNT_MIN,
                ShippingConditionOperator.GTE, 100_000, ShippingActionType.FREE_SHIPPING, 0, false);

        assertThatThrownBy(() -> service.create(merchantId, locationId, new ShippingPolicyUpsertRequest(
                ShippingMode.CONDITIONAL, 20_000L, "VND", ShippingRuleMatchMode.ANY,
                ShippingPolicyStatus.ACTIVE, List.of(disabledRule))))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.SHIPPING_RULE_INVALID);
    }
}
