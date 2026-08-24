package vn.covie.settings.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.api.dto.SystemSettingDefinitionResponse;
import vn.covie.settings.api.dto.SystemSettingDetailResponse;
import vn.covie.settings.api.dto.SystemSettingHistoryResponse;
import vn.covie.settings.api.dto.SystemSettingOverrideResponse;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingMutationResult;
import vn.covie.settings.application.SystemSettingService;
import vn.covie.settings.model.SettingScopeType;

@RestController
@RequestMapping("/api/v1/admin/settings")
public final class AdminSystemSettingController {
    private static final String PRIVATE_NO_STORE = "private, no-store";
    private final SystemSettingService service;

    public AdminSystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SystemSettingDefinitionResponse>> list() {
        return noStore(service.definitions());
    }

    @GetMapping("/{settingKey}")
    public ResponseEntity<SystemSettingDetailResponse> detail(
            @PathVariable String settingKey,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID locationAreaId) {
        return noStore(service.detail(settingKey, new SystemSettingContext(merchantId, categoryId, locationAreaId)));
    }

    @GetMapping("/{settingKey}/history")
    public ResponseEntity<Page<SystemSettingHistoryResponse>> history(
            @PathVariable String settingKey,
            @RequestParam SettingScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return noStore(service.history(settingKey, scopeType, scopeId, page, size));
    }

    @PatchMapping("/{settingKey}")
    public ResponseEntity<SystemSettingOverrideResponse> patch(
            @PathVariable String settingKey,
            @Valid @RequestBody PatchSystemSettingRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        SystemSettingMutationResult result = service.patch(settingKey, request, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                .eTag("\"v" + result.override().version() + "\"")
                .body(result.override());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }
}
