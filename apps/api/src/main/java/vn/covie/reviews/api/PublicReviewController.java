package vn.covie.reviews.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.reviews.api.dto.ReviewPageResponse;
import vn.covie.reviews.application.ReviewReadService;

@RestController
public class PublicReviewController {
    private final ReviewReadService reviews;
    public PublicReviewController(ReviewReadService reviews) { this.reviews = reviews; }
    @GetMapping("/api/v1/catalog/items/{itemId}/reviews")
    public ReviewPageResponse catalog(@PathVariable UUID itemId, @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) { return reviews.catalog(itemId, limit, cursor); }
    @GetMapping("/api/v1/merchants/{merchantId}/reviews")
    public ReviewPageResponse merchant(@PathVariable UUID merchantId, @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) { return reviews.merchant(merchantId, limit, cursor); }
}
