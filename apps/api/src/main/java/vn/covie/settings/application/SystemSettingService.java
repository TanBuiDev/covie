package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.api.dto.SystemSettingDefinitionResponse;
import vn.covie.settings.api.dto.SystemSettingDetailResponse;
import vn.covie.settings.api.dto.SystemSettingHistoryResponse;
import vn.covie.settings.api.dto.SystemSettingOverrideResponse;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.model.SystemSettingHistoryEntity;
import vn.covie.settings.persistence.SystemSettingHistoryRepository;
import vn.covie.settings.persistence.SystemSettingRepository;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public final class SystemSettingService {
    private final AdminAccessPolicy admins;
    private final SystemSettingRegistry registry;
    private final SystemSettingValueCodec codec;
    private final SystemSettingResolver resolver;
    private final SystemSettingRepository settings;
    private final SystemSettingHistoryRepository history;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final OutboxEventWriter outbox;
    private final EntityManager entityManager;

    public SystemSettingService(
            AdminAccessPolicy admins,
            SystemSettingRegistry registry,
            SystemSettingValueCodec codec,
            SystemSettingResolver resolver,
            SystemSettingRepository settings,
            SystemSettingHistoryRepository history,
            UuidV7Generator ids,
            AppClock clock,
            AuditLogWriter audit,
            RequestIdAccessor requestIds,
            OutboxEventWriter outbox,
            EntityManager entityManager) {
        this.admins = admins;
        this.registry = registry;
        this.codec = codec;
        this.resolver = resolver;
        this.settings = settings;
        this.history = history;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.requestIds = requestIds;
        this.outbox = outbox;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SystemSettingDefinitionResponse> definitions() {
        admins.requireAdmin();
        return registry.all().stream().map(SystemSettingDefinitionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SystemSettingDetailResponse detail(String key, SystemSettingContext context) {
        admins.requireAdmin();
        SystemSettingDefinition definition = registry.require(key);
        SystemSettingContext safe = context == null ? SystemSettingContext.globalOnly() : context;
        List<SystemSettingEntity> persisted = settings.findAllBySettingKeyOrderByScopeTypeAsc(key);
        JsonNode effective = resolver.resolveRaw(key, safe);
        SystemSettingOverrideResponse source = resolveSource(definition, safe, persisted);
        return new SystemSettingDetailResponse(
                key,
                definition.valueType(),
                definition.defaultValue(),
                definition.allowedScopes(),
                effective,
                source,
                persisted.stream().map(SystemSettingOverrideResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public Page<SystemSettingHistoryResponse> history(
            String key, SettingScopeType scopeType, UUID scopeId, int page, int size) {
        admins.requireAdmin();
        registry.require(key);
        validateScopeShape(scopeType, scopeId);
        SystemSettingEntity setting = exact(key, scopeType, scopeId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "System setting override was not found."));
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        return history.findAllBySettingIdOrderByChangedAtDescIdDesc(setting.getId(), PageRequest.of(safePage, safeSize))
                .map(SystemSettingHistoryResponse::from);
    }

    @Transactional
    public SystemSettingMutationResult patch(String key, PatchSystemSettingRequest request, String ifMatch) {
        CurrentActor actor = admins.requireAdmin();
        SystemSettingDefinition definition = registry.require(key);
        validateScope(definition, request.scopeType(), request.scopeId());
        codec.validate(key, request.value());
        validateScopeTargetExists(request.scopeType(), request.scopeId());
        Instant now = clock.now();
        Optional<SystemSettingEntity> current = exact(key, request.scopeType(), request.scopeId(), true);

        if (current.isEmpty()) {
            if (ifMatch != null && !ifMatch.isBlank()) {
                throw new ApiException(ErrorCode.SYSTEM_SETTING_OVERRIDE_ALREADY_EXISTS, HttpStatus.CONFLICT,
                        "If-Match cannot be supplied when creating a new override.");
            }
            SystemSettingEntity created = SystemSettingEntity.create(
                    ids.generate(), key, definition.valueType(), request.value(), request.scopeType(), request.scopeId(), actor.userId(), now);
            try {
                settings.saveAndFlush(created);
            } catch (DataIntegrityViolationException exception) {
                throw new ApiException(ErrorCode.SYSTEM_SETTING_OVERRIDE_ALREADY_EXISTS, HttpStatus.CONFLICT,
                        "System setting override already exists.");
            }
            history.save(SystemSettingHistoryEntity.create(
                    ids.generate(), created.getId(), null, created.getValueJson(), 0, created.getVersion(), actor.userId(), now));
            writeAuditAndOutbox(actor, created);
            return new SystemSettingMutationResult(true, SystemSettingOverrideResponse.from(created));
        }

        SystemSettingEntity setting = current.get();
        long expected = VersionPrecondition.parseRequired(ifMatch);
        VersionPrecondition.require(expected, setting.getVersion());
        JsonNode oldValue = setting.getValueJson().deepCopy();
        long oldVersion = setting.getVersion();
        setting.replaceValue(request.value(), actor.userId(), now);
        settings.saveAndFlush(setting);
        history.save(SystemSettingHistoryEntity.create(
                ids.generate(), setting.getId(), oldValue, setting.getValueJson(), oldVersion, setting.getVersion(), actor.userId(), now));
        writeAuditAndOutbox(actor, setting);
        return new SystemSettingMutationResult(false, SystemSettingOverrideResponse.from(setting));
    }

    private Optional<SystemSettingEntity> exact(String key, SettingScopeType scopeType, UUID scopeId, boolean lock) {
        return switch (scopeType) {
            case GLOBAL -> lock ? settings.lockGlobal(key, scopeType) : settings.findBySettingKeyAndScopeType(key, scopeType);
            case MERCHANT -> lock ? settings.lockMerchant(key, scopeType, scopeId)
                    : settings.findBySettingKeyAndScopeTypeAndMerchantProfileId(key, scopeType, scopeId);
            case CATEGORY -> lock ? settings.lockCategory(key, scopeType, scopeId)
                    : settings.findBySettingKeyAndScopeTypeAndCategoryId(key, scopeType, scopeId);
            case LOCATION_AREA -> lock ? settings.lockLocationArea(key, scopeType, scopeId)
                    : settings.findBySettingKeyAndScopeTypeAndLocationAreaId(key, scopeType, scopeId);
        };
    }

    private static void validateScope(SystemSettingDefinition definition, SettingScopeType scopeType, UUID scopeId) {
        validateScopeShape(scopeType, scopeId);
        if (!definition.allowedScopes().contains(scopeType)) {
            throw new ApiException(ErrorCode.SYSTEM_SETTING_SCOPE_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "System setting scope is not allowed for this key.");
        }
    }

    private static void validateScopeShape(SettingScopeType scopeType, UUID scopeId) {
        if (scopeType == null || (scopeType == SettingScopeType.GLOBAL) != (scopeId == null)) {
            throw new ApiException(ErrorCode.SYSTEM_SETTING_SCOPE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "System setting scope shape is invalid.");
        }
    }

    private void validateScopeTargetExists(SettingScopeType scopeType, UUID scopeId) {
        if (scopeType == SettingScopeType.GLOBAL) return;
        String table = switch (scopeType) {
            case MERCHANT -> "merchant_profiles";
            case CATEGORY -> "categories";
            case LOCATION_AREA -> "location_areas";
            case GLOBAL -> throw new IllegalStateException("GLOBAL handled above");
        };
        Object value = entityManager.createNativeQuery("select exists(select 1 from " + table + " where id=:id)")
                .setParameter("id", scopeId)
                .getSingleResult();
        boolean exists = value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
        if (!exists) {
            throw new ApiException(ErrorCode.SYSTEM_SETTING_SCOPE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "System setting scope target does not exist.");
        }
    }

    private SystemSettingOverrideResponse resolveSource(
            SystemSettingDefinition definition, SystemSettingContext context, List<SystemSettingEntity> persisted) {
        Map<SettingScopeType, UUID> expectedIds = new HashMap<>();
        expectedIds.put(SettingScopeType.MERCHANT, context.merchantId());
        expectedIds.put(SettingScopeType.CATEGORY, context.categoryId());
        expectedIds.put(SettingScopeType.LOCATION_AREA, context.locationAreaId());
        for (SettingScopeType scope : definition.resolutionOrder()) {
            UUID expectedId = expectedIds.get(scope);
            if (scope != SettingScopeType.GLOBAL && expectedId == null) continue;
            for (SystemSettingEntity setting : persisted) {
                if (setting.getScopeType() != scope) continue;
                UUID actual = switch (scope) {
                    case GLOBAL -> null;
                    case MERCHANT -> setting.getMerchantProfileId();
                    case CATEGORY -> setting.getCategoryId();
                    case LOCATION_AREA -> setting.getLocationAreaId();
                };
                if (java.util.Objects.equals(actual, expectedId)) return SystemSettingOverrideResponse.from(setting);
            }
        }
        return null;
    }

    private void writeAuditAndOutbox(CurrentActor actor, SystemSettingEntity setting) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("settingKey", setting.getSettingKey());
        payload.put("scopeType", setting.getScopeType().name());
        payload.put("version", setting.getVersion());
        UUID scopeId = switch (setting.getScopeType()) {
            case GLOBAL -> null;
            case MERCHANT -> setting.getMerchantProfileId();
            case CATEGORY -> setting.getCategoryId();
            case LOCATION_AREA -> setting.getLocationAreaId();
        };
        if (scopeId != null) payload.put("scopeId", scopeId.toString());
        outbox.append(new OutboxEventIntent("SYSTEM_SETTING_CHANGED", "SYSTEM_SETTING", setting.getId(), payload));
        audit.securityEvent(actor.userId(), "SYSTEM_SETTING_CHANGED", "SYSTEM_SETTING", setting.getId(), requestIds.current(), payload);
    }
}
