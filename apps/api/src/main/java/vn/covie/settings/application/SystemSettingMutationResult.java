package vn.covie.settings.application;

import vn.covie.settings.api.dto.SystemSettingOverrideResponse;

public record SystemSettingMutationResult(boolean created, SystemSettingOverrideResponse override) {}
