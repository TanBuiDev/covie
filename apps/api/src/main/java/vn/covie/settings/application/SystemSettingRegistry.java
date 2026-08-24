package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SettingValueType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class SystemSettingRegistry {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Map<String, SystemSettingDefinition> definitions;

    public SystemSettingRegistry() {
        LinkedHashMap<String, SystemSettingDefinition> defs = new LinkedHashMap<>();
        register(defs, bool("review.enabled", true, SettingScopeType.GLOBAL));
        register(defs, integer("reputation.warning_threshold_basis_points", 8000, 0, 10000, SettingScopeType.GLOBAL));
        register(defs, integer("reputation.minimum_eligible_orders_for_warning", 3, 0, 100000, SettingScopeType.GLOBAL));
        register(defs, integerScoped("need_request.max_response_items_per_location", 3, 1, 10,
                List.of(SettingScopeType.MERCHANT, SettingScopeType.CATEGORY, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL)));
        register(defs, integerScoped("order.confirmation_timeout_minutes", 30, 5, 1440,
                List.of(SettingScopeType.MERCHANT, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL)));
        register(defs, integerScoped("order.auto_complete_hours", 24, 1, 168,
                List.of(SettingScopeType.MERCHANT, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL)));
        register(defs, integerScoped("order.long_issue_timeout_hours", 48, 1, 720,
                List.of(SettingScopeType.MERCHANT, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL)));
        register(defs, integerScoped("pickup.no_show_grace_minutes", 120, 1, 1440,
                List.of(SettingScopeType.MERCHANT, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL)));
        register(defs, new SystemSettingDefinition(
                "moderation.mode", SettingValueType.ENUM, JSON.textNode("AUTO_PUBLISH"),
                Set.of(SettingScopeType.MERCHANT, SettingScopeType.CATEGORY, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL),
                List.of(SettingScopeType.MERCHANT, SettingScopeType.CATEGORY, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL),
                value -> value != null && value.isTextual() && Set.of("AUTO_PUBLISH", "PRE_APPROVAL", "HYBRID").contains(value.textValue())));
        register(defs, integer("moderation.hybrid_adverse_lookback_days", 90, 1, 3650, SettingScopeType.GLOBAL));
        register(defs, integer("bulk.max_items", 100, 1, 500, SettingScopeType.GLOBAL));
        definitions = Map.copyOf(defs);
    }

    public SystemSettingDefinition require(String key) {
        SystemSettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new ApiException(ErrorCode.SYSTEM_SETTING_KEY_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "System setting key is not allowlisted.");
        }
        return definition;
    }

    public List<SystemSettingDefinition> all() { return List.copyOf(definitions.values()); }

    private static void register(Map<String, SystemSettingDefinition> defs, SystemSettingDefinition def) {
        if (defs.putIfAbsent(def.key(), def) != null) throw new IllegalStateException("Duplicate system setting key: " + def.key());
    }

    private static SystemSettingDefinition bool(String key, boolean defaultValue, SettingScopeType scope) {
        return new SystemSettingDefinition(key, SettingValueType.BOOLEAN, JSON.booleanNode(defaultValue),
                Set.of(scope), List.of(scope), value -> value != null && value.isBoolean());
    }

    private static SystemSettingDefinition integer(String key, int defaultValue, int min, int max, SettingScopeType scope) {
        return new SystemSettingDefinition(key, SettingValueType.INTEGER, JSON.numberNode(defaultValue),
                Set.of(scope), List.of(scope), integerRange(min, max));
    }

    private static SystemSettingDefinition integerScoped(String key, int defaultValue, int min, int max, List<SettingScopeType> order) {
        return new SystemSettingDefinition(key, SettingValueType.INTEGER, JSON.numberNode(defaultValue),
                Set.copyOf(order), order, integerRange(min, max));
    }

    private static java.util.function.Predicate<JsonNode> integerRange(int min, int max) {
        return value -> value != null && value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() >= min && value.intValue() <= max;
    }
}
