package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.persistence.SystemSettingRepository;

@Service
public final class SystemSettingResolver {
    private final SystemSettingRegistry registry;
    private final SystemSettingRepository repository;
    private final SystemSettingValueCodec codec;

    public SystemSettingResolver(SystemSettingRegistry registry, SystemSettingRepository repository, SystemSettingValueCodec codec) {
        this.registry = registry;
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public JsonNode resolveRaw(String key, SystemSettingContext context) {
        SystemSettingDefinition def = registry.require(key);
        SystemSettingContext safeContext = context == null ? SystemSettingContext.globalOnly() : context;
        for (SettingScopeType scope : def.resolutionOrder()) {
            Optional<SystemSettingEntity> row = switch (scope) {
                case MERCHANT -> safeContext.merchantId() == null ? Optional.empty()
                        : repository.findBySettingKeyAndScopeTypeAndMerchantProfileId(key, scope, safeContext.merchantId());
                case CATEGORY -> safeContext.categoryId() == null ? Optional.empty()
                        : repository.findBySettingKeyAndScopeTypeAndCategoryId(key, scope, safeContext.categoryId());
                case LOCATION_AREA -> safeContext.locationAreaId() == null ? Optional.empty()
                        : repository.findBySettingKeyAndScopeTypeAndLocationAreaId(key, scope, safeContext.locationAreaId());
                case GLOBAL -> repository.findBySettingKeyAndScopeType(key, scope);
            };
            if (row.isPresent()) return validatedPersistedValue(def, row.get());
        }
        return def.defaultValue().deepCopy();
    }

    public boolean bool(String key, SystemSettingContext context) { return codec.booleanValue(key, resolveRaw(key, context)); }
    public int integer(String key, SystemSettingContext context) { return codec.integerValue(key, resolveRaw(key, context)); }
    public String enumName(String key, SystemSettingContext context) { return codec.enumValue(key, resolveRaw(key, context)); }

    private JsonNode validatedPersistedValue(SystemSettingDefinition def, SystemSettingEntity row) {
        if (row.getValueType() != def.valueType() || !def.validator().test(row.getValueJson())) {
            throw new IllegalStateException("Persisted system setting violates registry definition: " + def.key());
        }
        return row.getValueJson().deepCopy();
    }
}
