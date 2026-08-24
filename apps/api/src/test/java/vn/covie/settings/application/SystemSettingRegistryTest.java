package vn.covie.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class SystemSettingRegistryTest {

    private final SystemSettingRegistry registry = new SystemSettingRegistry();

    @Test
    void permitsOnlyTheExplicitV1KeysAndTheirScopes() {
        assertThat(registry.require("review.enabled").allowedScopes()).containsExactly(SettingScopeType.GLOBAL);
        assertThat(registry.require("moderation.mode").resolutionOrder()).containsExactly(
                SettingScopeType.MERCHANT, SettingScopeType.CATEGORY, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL);
        assertThatThrownBy(() -> registry.require("unknown.key"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.SYSTEM_SETTING_KEY_NOT_ALLOWED);
    }
}
