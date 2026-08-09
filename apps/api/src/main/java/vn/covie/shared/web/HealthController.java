package vn.covie.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public final class HealthController {
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}
