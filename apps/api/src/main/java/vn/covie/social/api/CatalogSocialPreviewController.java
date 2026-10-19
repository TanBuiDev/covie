package vn.covie.social.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.social.api.dto.SocialItemPreview;
import vn.covie.social.application.CatalogSocialPreviewService;

@RestController
public class CatalogSocialPreviewController {
    private final CatalogSocialPreviewService previews;
    public CatalogSocialPreviewController(CatalogSocialPreviewService previews) { this.previews=previews; }

    @GetMapping("/api/v1/catalog/social-previews")
    public ResponseEntity<Map<UUID,SocialItemPreview>> previews(
            @io.swagger.v3.oas.annotations.Parameter(required=true, array=@io.swagger.v3.oas.annotations.media.ArraySchema(
                    minItems=1,maxItems=40,schema=@io.swagger.v3.oas.annotations.media.Schema(type="string",format="uuid")))
            @RequestParam(required=false) List<String> itemIds) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(previews.previews(itemIds));
    }
}
