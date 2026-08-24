package vn.covie.settings.application;

import java.util.UUID;

public record SystemSettingContext(UUID merchantId, UUID categoryId, UUID locationAreaId) {
    public static SystemSettingContext globalOnly() {
        return new SystemSettingContext(null, null, null);
    }
}
