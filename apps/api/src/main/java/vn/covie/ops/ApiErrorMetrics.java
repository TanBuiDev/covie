package vn.covie.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ErrorCode;

@Component
public final class ApiErrorMetrics {
    private final MeterRegistry registry;
    public ApiErrorMetrics(MeterRegistry registry) { this.registry = registry; }
    public void record(ErrorCode code, int status) {
        Counter.builder("covie.api.errors")
                .tags("code", code.name(), "status", Integer.toString(status))
                .register(registry).increment();
    }
}
