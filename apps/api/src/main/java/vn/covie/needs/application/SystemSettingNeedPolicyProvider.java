package vn.covie.needs.application;

import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

@Component
@Primary
public final class SystemSettingNeedPolicyProvider implements NeedPolicyProvider {
    private final SystemSettingResolver settings;

    public SystemSettingNeedPolicyProvider(SystemSettingResolver settings) {
        this.settings = settings;
    }

    @Override
    public NeedPolicySnapshot resolve(UUID merchantId, UUID categoryId, UUID locationAreaId) {
        SystemSettingContext context = new SystemSettingContext(merchantId, categoryId, locationAreaId);
        return new NeedPolicySnapshot(settings.integer("need_request.max_response_items_per_location", context));
    }
}
