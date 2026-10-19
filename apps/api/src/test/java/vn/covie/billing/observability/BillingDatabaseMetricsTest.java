package vn.covie.billing.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingDatabaseMetricsTest {
    @Test
    void configurationGaugesFollowWorkerFlagAndGatewayCapabilityWithoutDatabaseQueries() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = mock(vn.covie.billing.payos.PayosGateway.class);
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new BillingDatabaseMetrics(jdbc, gateway, true);
            metrics.bindTo(registry);
            assertThat(registry.get("covie.billing.reconciliation.enabled").gauge().value()).isEqualTo(1);
            assertThat(registry.get("covie.billing.payment.creation.enabled").gauge().value()).isZero();
            when(gateway.isEnabled()).thenReturn(true);
            assertThat(registry.get("covie.billing.payment.creation.enabled").gauge().value()).isEqualTo(1);
            verifyNoInteractions(jdbc);
        } finally {
            registry.close();
        }
    }

    @Test
    void failedRefreshPublishesUnknownNotZeroAndRetainsTheLastSuccessWithoutScrapeSql() {
        var jdbc = mock(JdbcTemplate.class);
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new BillingDatabaseMetrics(jdbc, mock(vn.covie.billing.payos.PayosGateway.class), false);
            metrics.bindTo(registry);
            assertThat(registry.get("covie.billing.reconciliation.enabled").gauge().value()).isZero();
            assertThat(registry.get("covie.billing.invoices").tag("status", "PAID").gauge().value()).isNaN();
            when(jdbc.queryForMap(anyString())).thenReturn(Map.of("entitled", 0, "pending", 0, "review_required", 0, "age", 0));
            metrics.refresh();
            assertThat(registry.get("covie.billing.invoices").tag("status", "PAID").gauge().value()).isZero();
            double successfulAt = registry.get("covie.billing.database.last.success.unixtime").gauge().value();
            assertThat(successfulAt).isPositive();
            when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException("fixture database unavailable"));
            metrics.refresh();
            clearInvocations(jdbc);
            assertThat(registry.get("covie.billing.invoices").tag("status", "PAID").gauge().value()).isNaN();
            assertThat(registry.get("covie.billing.reconciliation.pending").gauge().value()).isNaN();
            assertThat(registry.get("covie.billing.reconciliation.review.required").gauge().value()).isNaN();
            assertThat(registry.get("covie.billing.database.last.success.unixtime").gauge().value()).isEqualTo(successfulAt);
            assertThat(registry.get("covie.billing.database.refresh.failures").counter().count()).isEqualTo(1);
            verifyNoInteractions(jdbc);
        } finally {
            registry.close();
        }
    }
}
