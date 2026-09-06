package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.location.persistence.LocationAreaRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.api.dto.SystemSettingDefinitionResponse;
import vn.covie.settings.api.dto.SystemSettingResponse;
import vn.covie.settings.api.dto.SystemSettingDetailResponse;
import vn.covie.settings.api.dto.SystemSettingOverrideResponse;
import vn.covie.settings.api.dto.SystemSettingHistoryResponse;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.model.SystemSettingHistoryEntity;
import vn.covie.settings.persistence.SystemSettingHistoryRepository;
import vn.covie.settings.persistence.SystemSettingRepository;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class SystemSettingService {
    private final AdminAccessPolicy admin; private final SystemSettingRegistry registry; private final SystemSettingRepository settings; private final SystemSettingHistoryRepository history; private final AuditLogWriter audit; private final OutboxEventWriter outbox; private final RequestIdAccessor requestIds; private final UuidV7Generator ids; private final AppClock clock; private final MerchantProfileRepository merchants; private final CategoryRepository categories; private final LocationAreaRepository areas;
    public SystemSettingService(AdminAccessPolicy admin, SystemSettingRegistry registry, SystemSettingRepository settings, SystemSettingHistoryRepository history, AuditLogWriter audit, OutboxEventWriter outbox, RequestIdAccessor requestIds, UuidV7Generator ids, AppClock clock, MerchantProfileRepository merchants, CategoryRepository categories, LocationAreaRepository areas) { this.admin=admin; this.registry=registry; this.settings=settings; this.history=history; this.audit=audit; this.outbox=outbox; this.requestIds=requestIds; this.ids=ids; this.clock=clock; this.merchants=merchants; this.categories=categories; this.areas=areas; }
    @Transactional public MutationResult patch(String key, String ifMatch, PatchSystemSettingRequest request) {
        var actor=admin.requireAdmin(); if(request==null||request.scopeType()==null||request.value()==null) throw invalid("A scope and value are required."); registry.validateScope(key,request.scopeType()); registry.validateValue(key,request.value()); validateScopeShape(request.scopeType(),request.scopeId()); Optional<SystemSettingEntity> existing=find(key,request.scopeType(),request.scopeId()); Instant now=clock.now(); SystemSettingEntity setting; boolean created=existing.isEmpty(); JsonNode oldValue=null; long oldVersion=0;
        if(created) { if(ifMatch!=null&&!ifMatch.isBlank()) throw invalid("If-Match must be omitted when creating an override."); setting=create(key,request,actor.userId(),now); setting=settings.saveAndFlush(setting); }
        else { setting=existing.get(); oldValue=setting.getValueJson(); oldVersion=setting.getVersion(); VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch),oldVersion); setting.replaceValue(request.value(),actor.userId(),now); setting=settings.saveAndFlush(setting); }
        history.save(SystemSettingHistoryEntity.create(ids.generate(),setting.getId(),oldValue,setting.getValueJson(),oldVersion,setting.getVersion(),actor.userId(),now)); Map<String,Object> payload=payload(setting); outbox.append(new OutboxEventIntent("SYSTEM_SETTING_CHANGED","SYSTEM_SETTING",setting.getId(),payload)); audit.securityEvent(actor.userId(),"SYSTEM_SETTING_CHANGED","SYSTEM_SETTING",setting.getId(),requestIds.current(),payload); return new MutationResult(setting,created);
    }
    @Transactional(readOnly = true) public java.util.List<SystemSettingDefinitionResponse> definitions() { admin.requireAdmin(); return registry.definitions().stream().map(d -> new SystemSettingDefinitionResponse(d.key(), d.valueType(), unwrap(d.defaultValue()), d.allowedScopes(), d.resolutionOrder())).toList(); }
    @Transactional(readOnly = true) public SystemSettingDetailResponse detail(String key, SystemSettingContext context) { admin.requireAdmin(); var definition=registry.require(key); var overrides=settings.findAllBySettingKeyOrderByScopeTypeAsc(key); SystemSettingEntity source=null; SystemSettingContext ctx=context==null?SystemSettingContext.globalOnly():context; for(SettingScopeType scope:definition.resolutionOrder()){UUID scopeId=switch(scope){case GLOBAL->null;case MERCHANT->ctx.merchantId();case CATEGORY->ctx.categoryId();case LOCATION_AREA->ctx.locationAreaId();};if(scope!=SettingScopeType.GLOBAL&&scopeId==null)continue;var candidate=find(key,scope,scopeId);if(candidate.isPresent()){source=candidate.get();break;}} return new SystemSettingDetailResponse(key,definition.valueType(),unwrap(definition.defaultValue()),definition.allowedScopes(),unwrap(source==null?definition.defaultValue():source.getValueJson()),source==null?null:override(source),overrides.stream().map(this::override).toList()); }
    @Transactional(readOnly = true) public org.springframework.data.domain.Page<SystemSettingHistoryResponse> history(String key, SettingScopeType scope, UUID scopeId, int page, int size) { admin.requireAdmin(); if(page<0||size<1||size>100)throw invalid("History page or size is invalid."); registry.validateScope(key,scope); validateScopeShape(scope,scopeId); var setting=find(key,scope,scopeId).orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Setting override was not found.")); return history.findAllBySettingIdOrderByChangedAtDescIdDesc(setting.getId(),org.springframework.data.domain.PageRequest.of(page,size)).map(h->new SystemSettingHistoryResponse(h.getId(),h.getOldValueJson(),h.getNewValueJson(),h.getOldVersion(),h.getNewVersion(),h.getChangedByUserId(),h.getChangedAt())); }
    public SystemSettingResponse response(SystemSettingEntity setting) { UUID scopeId = setting.getMerchantProfileId() != null ? setting.getMerchantProfileId() : setting.getCategoryId() != null ? setting.getCategoryId() : setting.getLocationAreaId(); return new SystemSettingResponse(setting.getId(), setting.getSettingKey(), setting.getValueType(), setting.getValueJson(), setting.getScopeType(), scopeId, setting.getVersion(), setting.getUpdatedByUserId(), setting.getUpdatedAt()); }
    private SystemSettingOverrideResponse override(SystemSettingEntity setting){UUID scopeId=setting.getMerchantProfileId()!=null?setting.getMerchantProfileId():setting.getCategoryId()!=null?setting.getCategoryId():setting.getLocationAreaId();return new SystemSettingOverrideResponse(setting.getId(),setting.getScopeType(),scopeId,unwrap(setting.getValueJson()),setting.getVersion(),setting.getUpdatedAt());}
    public static Object unwrap(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.doubleValue();
        if (node.isTextual()) return node.textValue();
        try { return new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(node, Object.class); }
        catch (Exception e) { return node.toString(); }
    }
    private Optional<SystemSettingEntity> find(String key, SettingScopeType scope, UUID id) { return switch(scope){case GLOBAL->settings.findBySettingKeyAndScopeType(key,scope);case MERCHANT->settings.findBySettingKeyAndScopeTypeAndMerchantProfileId(key,scope,id);case CATEGORY->settings.findBySettingKeyAndScopeTypeAndCategoryId(key,scope,id);case LOCATION_AREA->settings.findBySettingKeyAndScopeTypeAndLocationAreaId(key,scope,id);}; }
    private SystemSettingEntity create(String key, PatchSystemSettingRequest request, UUID actor, Instant now) { var def=registry.require(key); return switch(request.scopeType()){case GLOBAL->SystemSettingEntity.global(ids.generate(),key,def.valueType(),request.value(),actor,now);case MERCHANT->SystemSettingEntity.merchant(ids.generate(),key,def.valueType(),request.value(),request.scopeId(),actor,now);case CATEGORY->SystemSettingEntity.category(ids.generate(),key,def.valueType(),request.value(),request.scopeId(),actor,now);case LOCATION_AREA->SystemSettingEntity.locationArea(ids.generate(),key,def.valueType(),request.value(),request.scopeId(),actor,now);}; }
    private void validateScopeShape(SettingScopeType scope, UUID id) { if(scope==SettingScopeType.GLOBAL){if(id!=null)throw invalid("Global settings cannot have a scope id.");return;} if(id==null)throw invalid("Scoped settings require a scope id."); boolean exists=switch(scope){case MERCHANT->merchants==null||merchants.existsById(id);case CATEGORY->categories==null||categories.existsById(id);case LOCATION_AREA->areas==null||areas.existsById(id);case GLOBAL->true;}; if(!exists)throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Setting scope target was not found."); }
    private static Map<String,Object> payload(SystemSettingEntity s){Map<String,Object> p=new HashMap<>();p.put("settingKey",s.getSettingKey());p.put("scopeType",s.getScopeType().name());p.put("version",s.getVersion()); UUID id=s.getMerchantProfileId()!=null?s.getMerchantProfileId():s.getCategoryId()!=null?s.getCategoryId():s.getLocationAreaId();if(id!=null)p.put("scopeId",id.toString());return Map.copyOf(p);}
    private static ApiException invalid(String message){return new ApiException(ErrorCode.SYSTEM_SETTING_VALUE_INVALID,HttpStatus.UNPROCESSABLE_ENTITY,message);}
    public record MutationResult(SystemSettingEntity setting, boolean created) {}
}
