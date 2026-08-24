package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NeedPolicyProviderTest {
    @Test void bootstrapPolicyReturnsConfiguredResponseItemLimit() {
        PropertyNeedPolicyProvider provider = new PropertyNeedPolicyProvider();
        provider.setMaxResponseItems(4);
        assertThat(provider.resolve(UUID.randomUUID(), UUID.randomUUID(), null).maxResponseItems()).isEqualTo(4);
    }
}
