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
public class SystemSettingRegistry {
    private static final List<SettingScopeType> GLOBAL = List.of(SettingScopeType.GLOBAL);
    private static final List<SettingScopeType> MERCHANT_CATEGORY_AREA_GLOBAL = List.of(SettingScopeType.MERCHANT, SettingScopeType.CATEGORY, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL);
    private static final List<SettingScopeType> MERCHANT_AREA_GLOBAL = List.of(SettingScopeType.MERCHANT, SettingScopeType.LOCATION_AREA, SettingScopeType.GLOBAL);
    private final Map<String, SystemSettingDefinition> definitions;
    public SystemSettingRegistry() {
        JsonNodeFactory json = JsonNodeFactory.instance;
        Map<String, SystemSettingDefinition> values = new LinkedHashMap<>();
        add(values, bool("review.enabled", true));
        add(values, integer("reputation.warning_threshold_basis_points", 8000, 0, 10000, GLOBAL));
        add(values, integer("reputation.minimum_eligible_orders_for_warning", 3, 0, 100000, GLOBAL));
        add(values, integer("need_request.max_response_items_per_location", 3, 1, 10, MERCHANT_CATEGORY_AREA_GLOBAL));
        add(values, integer("order.confirmation_timeout_minutes", 30, 5, 1440, MERCHANT_AREA_GLOBAL));
        add(values, integer("order.auto_complete_hours", 24, 1, 168, MERCHANT_AREA_GLOBAL));
        add(values, integer("order.long_issue_timeout_hours", 48, 1, 720, MERCHANT_AREA_GLOBAL));
        add(values, integer("pickup.no_show_grace_minutes", 120, 1, 1440, MERCHANT_AREA_GLOBAL));
        add(values, new SystemSettingDefinition("moderation.mode", SettingValueType.ENUM, json.textNode("AUTO_PUBLISH"), Set.copyOf(MERCHANT_CATEGORY_AREA_GLOBAL), MERCHANT_CATEGORY_AREA_GLOBAL, value -> value.isTextual() && Set.of("AUTO_PUBLISH", "PRE_APPROVAL", "HYBRID").contains(value.textValue())));
        add(values, integer("moderation.hybrid_adverse_lookback_days", 90, 1, 3650, GLOBAL));
        add(values, integer("bulk.max_items", 100, 1, 500, GLOBAL));
        // Billing flags are deliberately OFF by default. Enforce only after legacy grants/pilot acceptance.
        add(values, bool("billing.enforcement_enabled", false));
        add(values, bool("billing.seller_registration_enabled", true));
        add(values, bool("billing.reminders_enabled", false));
        add(values, bool("billing.finance_roles_enabled", false));
        add(values, integer("billing.checkout_hourly_limit", 6, 1, 60, GLOBAL));
        definitions = Map.copyOf(values);
    }
    public SystemSettingDefinition require(String key) { SystemSettingDefinition definition = definitions.get(key); if (definition == null) throw invalidKey(key); return definition; }
    public List<SystemSettingDefinition> definitions() { return definitions.values().stream().toList(); }
    public boolean isValidValue(String key, JsonNode value) { return require(key).validator().test(value); }
    public void validateValue(String key, JsonNode value) { if (!require(key).validator().test(value)) throw invalidValue(key); }
    public void validateScope(String key, SettingScopeType scope) { if (!require(key).allowedScopes().contains(scope)) throw invalidScope(key); }
    private static SystemSettingDefinition bool(String key, boolean defaultValue) { return new SystemSettingDefinition(key, SettingValueType.BOOLEAN, JsonNodeFactory.instance.booleanNode(defaultValue), Set.of(SettingScopeType.GLOBAL), GLOBAL, JsonNode::isBoolean); }
    private static SystemSettingDefinition integer(String key, int defaultValue, int min, int max, List<SettingScopeType> scopes) { return new SystemSettingDefinition(key, SettingValueType.INTEGER, JsonNodeFactory.instance.numberNode(defaultValue), Set.copyOf(scopes), scopes, value -> value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= min && value.intValue() <= max); }
    private static void add(Map<String, SystemSettingDefinition> values, SystemSettingDefinition definition) { values.put(definition.key(), definition); }
    private static ApiException invalidKey(String key) { return new ApiException(ErrorCode.SYSTEM_SETTING_KEY_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY, "System setting key is not allowed.", Map.of("key", key)); }
    private static ApiException invalidValue(String key) { return new ApiException(ErrorCode.SYSTEM_SETTING_VALUE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "System setting value is invalid.", Map.of("key", key)); }
    private static ApiException invalidScope(String key) { return new ApiException(ErrorCode.SYSTEM_SETTING_SCOPE_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY, "System setting scope is not allowed.", Map.of("key", key)); }
}
