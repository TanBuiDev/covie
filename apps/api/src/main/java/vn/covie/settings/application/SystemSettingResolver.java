package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.persistence.SystemSettingRepository;

@Service
public class SystemSettingResolver {
    private final SystemSettingRegistry registry;
    private final SystemSettingRepository repository;
    private final SystemSettingValueCodec codec;
    public SystemSettingResolver(SystemSettingRegistry registry, SystemSettingRepository repository, SystemSettingValueCodec codec) { this.registry = registry; this.repository = repository; this.codec = codec; }
    @Transactional(readOnly = true)
    public JsonNode resolveRaw(String key, SystemSettingContext context) {
        SystemSettingDefinition definition = registry.require(key);
        SystemSettingContext effectiveContext = context == null ? SystemSettingContext.globalOnly() : context;
        for (SettingScopeType scope : definition.resolutionOrder()) {
            Optional<SystemSettingEntity> row = switch (scope) {
                case MERCHANT -> effectiveContext.merchantId() == null ? Optional.empty() : repository.findBySettingKeyAndScopeTypeAndMerchantProfileId(key, scope, effectiveContext.merchantId());
                case CATEGORY -> effectiveContext.categoryId() == null ? Optional.empty() : repository.findBySettingKeyAndScopeTypeAndCategoryId(key, scope, effectiveContext.categoryId());
                case LOCATION_AREA -> effectiveContext.locationAreaId() == null ? Optional.empty() : repository.findBySettingKeyAndScopeTypeAndLocationAreaId(key, scope, effectiveContext.locationAreaId());
                case GLOBAL -> repository.findBySettingKeyAndScopeType(key, scope);
            };
            if (row.isPresent()) {
                SystemSettingEntity setting = row.get();
                if (setting.getValueType() != definition.valueType()) throw new IllegalStateException("Persisted system setting has an incompatible value type: " + key);
                if (!registry.isValidValue(key, setting.getValueJson())) throw new IllegalStateException("Persisted system setting has an invalid value: " + key);
                return setting.getValueJson();
            }
        }
        return definition.defaultValue();
    }
    public boolean bool(String key, SystemSettingContext context) { return codec.booleanValue(key, resolveRaw(key, context)); }
    public int integer(String key, SystemSettingContext context) { return codec.integerValue(key, resolveRaw(key, context)); }
    public String enumName(String key, SystemSettingContext context) { return codec.enumValue(key, resolveRaw(key, context)); }
}
