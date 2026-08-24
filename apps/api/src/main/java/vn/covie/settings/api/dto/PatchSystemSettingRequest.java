package vn.covie.settings.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import vn.covie.settings.model.SettingScopeType;

public record PatchSystemSettingRequest(
        @NotNull SettingScopeType scopeType,
        UUID scopeId,
        @NotNull JsonNode value) {}
