package vn.covie.social.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.social.api.dto.CreateSocialCommentRequest;
import vn.covie.social.api.dto.SocialComment;
import vn.covie.social.api.dto.SocialCommentPage;
import vn.covie.social.api.dto.SocialItemSummary;
import vn.covie.social.application.CatalogSocialService;

/** Public catalog-social API. Authorization and CSRF are enforced by the existing security chain. */
@RestController
public class CatalogSocialController {
    private final CatalogSocialService service;
    public CatalogSocialController(CatalogSocialService service) { this.service=service; }

    @GetMapping("/api/v1/catalog/social-summary")
    public Map<UUID,SocialItemSummary> summary(@RequestParam List<UUID> itemIds) {
        return service.summaries(itemIds);
    }

    @GetMapping("/api/v1/catalog/items/{itemId}/comments")
    public SocialCommentPage comments(@PathVariable UUID itemId,
            @RequestParam(required=false) Integer limit,@RequestParam(required=false) UUID before) {
        return service.comments(itemId,limit,before);
    }

    @PutMapping("/api/v1/me/catalog/items/{itemId}/likes")
    public SocialItemSummary like(@PathVariable UUID itemId) { return service.react(itemId,"LIKE",true); }

    @DeleteMapping("/api/v1/me/catalog/items/{itemId}/likes")
    public SocialItemSummary unlike(@PathVariable UUID itemId) { return service.react(itemId,"LIKE",false); }

    @PutMapping("/api/v1/me/catalog/items/{itemId}/saves")
    public SocialItemSummary save(@PathVariable UUID itemId) { return service.react(itemId,"SAVE",true); }

    @DeleteMapping("/api/v1/me/catalog/items/{itemId}/saves")
    public SocialItemSummary unsave(@PathVariable UUID itemId) { return service.react(itemId,"SAVE",false); }

    @PostMapping("/api/v1/me/catalog/items/{itemId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialComment comment(@PathVariable UUID itemId,@Valid @RequestBody CreateSocialCommentRequest body) {
        return service.addComment(itemId,body.body());
    }

    @DeleteMapping("/api/v1/me/catalog/items/{itemId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID itemId,@PathVariable UUID commentId) {
        service.deleteComment(itemId,commentId);
    }
}
