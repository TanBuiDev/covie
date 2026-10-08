package vn.covie.settings.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import vn.covie.settings.model.SettingScopeType;

public record PatchSystemSettingRequest(@NotNull SettingScopeType scopeType, UUID scopeId, @NotNull Object value) {}
