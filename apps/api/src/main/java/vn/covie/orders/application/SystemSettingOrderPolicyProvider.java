package vn.covie.orders.application;

import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
@Primary
public final class SystemSettingOrderPolicyProvider implements OrderPolicyProvider {
    private final SystemSettingResolver settings;
    private final MerchantLocationRepository locations;

    public SystemSettingOrderPolicyProvider(SystemSettingResolver settings, MerchantLocationRepository locations) {
        this.settings = settings;
        this.locations = locations;
    }

    @Override
    public OrderPolicySnapshot resolve(UUID merchantId, UUID merchantLocationId) {
        MerchantLocationEntity location = locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(merchantLocationId, merchantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Merchant location was not found."));
        SystemSettingContext context = new SystemSettingContext(merchantId, null, location.getLocationAreaId());
        return new OrderPolicySnapshot(
                Duration.ofMinutes(settings.integer("order.confirmation_timeout_minutes", context)),
                Duration.ofHours(settings.integer("order.auto_complete_hours", context)),
                Duration.ofMinutes(settings.integer("pickup.no_show_grace_minutes", context)),
                Duration.ofHours(settings.integer("order.long_issue_timeout_hours", context)));
    }
}
