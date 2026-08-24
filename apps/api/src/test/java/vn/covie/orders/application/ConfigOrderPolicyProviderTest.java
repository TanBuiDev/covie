package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigOrderPolicyProviderTest {
    @Test
    void resolvesTheConfiguredBootstrapPolicyForEveryMerchant() {
        OrderLifecycleProperties properties = new OrderLifecycleProperties(
                Duration.ofMinutes(30), Duration.ofHours(24), Duration.ofHours(2), Duration.ofDays(2));

        OrderPolicySnapshot policy = new ConfigOrderPolicyProvider(properties).resolve(UUID.randomUUID(), UUID.randomUUID());

        assertThat(policy.confirmationTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.autoCompleteGrace()).isEqualTo(Duration.ofHours(24));
        assertThat(policy.pickupNoShowGrace()).isEqualTo(Duration.ofHours(2));
        assertThat(policy.issueCloseTimeout()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void rejectsNonPositiveLifecycleDurations() {
        assertThatThrownBy(() -> new OrderPolicySnapshot(Duration.ZERO, Duration.ofHours(1), Duration.ofHours(1), Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("confirmationTimeout must be positive");
    }
}
