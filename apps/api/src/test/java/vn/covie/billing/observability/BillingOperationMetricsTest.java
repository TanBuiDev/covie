package vn.covie.billing.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BillingOperationMetricsTest {
    @Test
    void registryFailureCannotFailACompletedFinancialOperationOrReplaceItsOriginalError() {
        var registry = mock(MeterRegistry.class);
        when(registry.timer(anyString(), any(String[].class))).thenThrow(new IllegalStateException("fixture metrics unavailable"));
        var metrics = new BillingOperationMetrics(registry);
        var calls = new AtomicInteger();
        assertThat(metrics.checkout(() -> { calls.incrementAndGet(); return "original-result"; })).isEqualTo("original-result");
        assertThat(calls).hasValue(1);
        var original = new IllegalStateException("original business failure");
        assertThat(catchThrowable(() -> metrics.webhook(() -> { throw original; }))).isSameAs(original);
    }
}
