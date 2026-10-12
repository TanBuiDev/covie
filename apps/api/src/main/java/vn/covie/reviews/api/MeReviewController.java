package vn.covie.reviews.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.reviews.api.dto.CreateReviewRequest;
import vn.covie.reviews.api.dto.ReviewResponse;
import vn.covie.reviews.application.ReviewService;

@RestController
@RequestMapping("/api/v1/me/reviews")
public class MeReviewController {
    private final ReviewService service;
    public MeReviewController(ReviewService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody CreateReviewRequest request,
            @io.swagger.v3.oas.annotations.Parameter(required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.create(request, key);
    }
}
