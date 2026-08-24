package vn.covie.settings.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.settings.api.dto.PatchSystemSettingRequest;
import vn.covie.settings.api.dto.SystemSettingDefinitionResponse;
import vn.covie.settings.api.dto.SystemSettingResponse;
import vn.covie.settings.application.SystemSettingService;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSystemSettingController {
    private final SystemSettingService service;
    public AdminSystemSettingController(SystemSettingService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<SystemSettingDefinitionResponse>> list() { return noStore(service.definitions()); }
    @PatchMapping("/{settingKey}") public ResponseEntity<SystemSettingResponse> patch(@PathVariable String settingKey, @RequestHeader(value = "If-Match", required = false) String ifMatch, @Valid @RequestBody PatchSystemSettingRequest request) { var result=service.patch(settingKey,ifMatch,request); return ResponseEntity.status(result.created()?201:200).cacheControl(CacheControl.noStore().cachePrivate()).eTag(vn.covie.shared.concurrency.VersionPrecondition.format(result.setting().getVersion())).body(service.response(result.setting())); }
    private static <T> ResponseEntity<T> noStore(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body); }
}
