package vn.covie.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shipping.api.dto.ShippingPolicyUpsertRequest;
import vn.covie.shipping.api.dto.ShippingRuleRequest;
import vn.covie.shipping.model.*;
import vn.covie.shipping.persistence.ShippingRuleRepository;
import vn.covie.support.PostgresIntegrationTest;

class ShippingPolicyServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired ShippingPolicyService service;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired ShippingRuleRepository rules;
    @MockitoBean MerchantAccessPolicy access;

    @Test
    void replacesConditionalRulesAtomicallyAndReturnsTheNewOrderedSet() {
        UUID merchantId = UUID.randomUUID(), locationId = UUID.randomUUID(); Instant now = Instant.now();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Store", "store-" + merchantId, now));
        locations.saveAndFlush(MerchantLocationEntity.create(locationId, merchantId, "Main", "Address", 10.8, 106.7, now));
        when(access.requireLocation(merchantId, locationId, vn.covie.merchants.security.MerchantCapability.MANAGE_LOCATION_OFFER)).thenReturn(null);
        ShippingRuleRequest first = rule(100_000, 0), second = rule(200_000, 1);
        var created = service.create(merchantId, locationId, conditional(List.of(first, second)));
        var updated = service.update(merchantId, locationId, created.id(), VersionPrecondition.format(created.version()), conditional(List.of(second)));
        assertThat(updated.rules()).containsExactly(second);
        assertThat(rules.findByShippingPolicyIdOrderByPriority(created.id())).hasSize(1);
    }

    @Test
    void listsPoliciesInsideAReadOnlyTransactionWithoutRequestingAWriteLock() {
        UUID merchantId = UUID.randomUUID(), locationId = UUID.randomUUID(); Instant now = Instant.now();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Store", "store-" + merchantId, now));
        locations.saveAndFlush(MerchantLocationEntity.create(locationId, merchantId, "Main", "Address", 10.8, 106.7, now));
        when(access.requireLocation(merchantId, locationId, vn.covie.merchants.security.MerchantCapability.MANAGE_LOCATION_OFFER)).thenReturn(null);
        var created = service.create(merchantId, locationId, conditional(List.of(rule(100_000, 0))));

        assertThat(service.list(merchantId, locationId)).extracting("id").containsExactly(created.id());
    }

    private static ShippingPolicyUpsertRequest conditional(List<ShippingRuleRequest> rules) {
        return new ShippingPolicyUpsertRequest(ShippingMode.CONDITIONAL, 20_000L, "VND", ShippingRuleMatchMode.ANY, ShippingPolicyStatus.ACTIVE, rules);
    }
    private static ShippingRuleRequest rule(long threshold, int priority) {
        return new ShippingRuleRequest(ShippingConditionType.ORDER_AMOUNT_MIN, ShippingConditionOperator.GTE, threshold, ShippingActionType.FREE_SHIPPING, priority, true);
    }
}
