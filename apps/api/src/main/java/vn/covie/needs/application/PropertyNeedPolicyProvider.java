package vn.covie.needs.application;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "covie.needs")
public final class PropertyNeedPolicyProvider implements NeedPolicyProvider {
    private int maxResponseItems = 3;
    @Override public NeedPolicySnapshot resolve(UUID merchantId, UUID categoryId, UUID locationAreaId) { return new NeedPolicySnapshot(maxResponseItems); }
    public void setMaxResponseItems(int maxResponseItems) { this.maxResponseItems = maxResponseItems; }
}
