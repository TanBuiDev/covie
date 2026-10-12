package vn.covie.ops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ErrorCode;
import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorMetricsTest {
    @Test
    void groupsStableErrorCodeAndStatusOnly() {
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new ApiErrorMetrics(registry);
            metrics.record(ErrorCode.RESOURCE_VERSION_CONFLICT, 409);
            metrics.record(ErrorCode.RESOURCE_VERSION_CONFLICT, 409);
            metrics.record(ErrorCode.RESOURCE_NOT_FOUND, 404);
            var counter = registry.get("covie.api.errors").tags("code", "RESOURCE_VERSION_CONFLICT", "status", "409").counter();
            assertThat(counter.count()).isEqualTo(2);
            assertThat(counter.getId().getTags()).hasSize(2);
            assertThat(registry.get("covie.api.errors").tags("code", "RESOURCE_NOT_FOUND", "status", "404").counter().count()).isEqualTo(1);
        } finally { registry.close(); }
    }
}
