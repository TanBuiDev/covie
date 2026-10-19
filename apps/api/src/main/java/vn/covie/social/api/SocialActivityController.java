package vn.covie.social.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.social.api.dto.SocialActivityPage;
import vn.covie.social.api.dto.SocialActivityType;
import vn.covie.social.application.SocialActivityReadService;

@RestController
public class SocialActivityController {
    private final SocialActivityReadService service;
    public SocialActivityController(SocialActivityReadService service) { this.service=service; }

    @GetMapping("/api/v1/me/social/activity")
    public ResponseEntity<SocialActivityPage> activity(@RequestParam SocialActivityType type,
            @io.swagger.v3.oas.annotations.Parameter(schema=@io.swagger.v3.oas.annotations.media.Schema(type="integer",format="int32",minimum="1",maximum="50",defaultValue="20"))
            @RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.activity(type,limit,cursor));
    }
}
