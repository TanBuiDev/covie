package vn.covie.billing.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiException;

@Component
public final class BillingOperationMetrics {
    private static final Set<String> WEBHOOK_OUTCOMES = Set.of("settled", "duplicate", "ignored", "sample");
    private final MeterRegistry registry;

    public BillingOperationMetrics(MeterRegistry registry) { this.registry = registry; }

    public <T> T checkout(Supplier<T> action) {
        return observe("checkout", action, result -> "completed");
    }

    public String webhook(Supplier<String> action) {
        return observe("webhook", action, result -> result != null && WEBHOOK_OUTCOMES.contains(result) ? result : "other");
    }

    public void entitlement(long started, boolean batch) {
        record(batch ? "entitlement_batch" : "entitlement", "observed", started);
    }

    private <T> T observe(String operation, Supplier<T> action, Function<T, String> outcome) {
        long started = System.nanoTime();
        try {
            T result = action.get();
            record(operation, outcome.apply(result), started);
            return result;
        } catch (RuntimeException failure) {
            record(operation, failure instanceof ApiException api && api.status().is4xxClientError() ? "rejected" : "failed", started);
            throw failure;
        }
    }

    private void record(String operation, String outcome, long started) {
        try {
            registry.timer("covie.billing.operations", "operation", operation, "outcome", outcome)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) { }
    }
}
