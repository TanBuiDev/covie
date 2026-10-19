package vn.covie.billing.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.covie.billing.payos.PayosGateway;

@Component
public final class BillingDatabaseMetrics implements MeterBinder {
    private static final List<String> INVOICE_STATES = List.of("PENDING_PAYMENT", "PAID", "VOID", "REFUNDED", "DISPUTED");
    private static final List<String> SUBSCRIPTION_STATES = List.of("DRAFT", "PENDING_PAYMENT", "TRIALING", "ACTIVE", "PAST_DUE", "EXPIRED", "CANCELLED", "REVOKED");
    private final JdbcTemplate jdbc;
    private final PayosGateway gateway;
    private final boolean reconciliationEnabled;
    private volatile Snapshot snapshot = unavailable(0);
    private Counter failures;

    private record Snapshot(Map<String, Double> invoices, Map<String, Double> subscriptions,
                            double entitled, double pending, double reviewRequired, double age, double lastSuccess) {}

    public BillingDatabaseMetrics(JdbcTemplate jdbc, PayosGateway gateway,
            @Value("${COVIE_BILLING_RECONCILE_ENABLED:false}") boolean reconciliationEnabled) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.reconciliationEnabled = reconciliationEnabled;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String state : INVOICE_STATES) {
            Gauge.builder("covie.billing.invoices", this, metrics -> metrics.snapshot.invoices().get(state))
                .tag("status", state).register(registry);
        }
        for (String state : SUBSCRIPTION_STATES) {
            Gauge.builder("covie.billing.subscriptions", this, metrics -> metrics.snapshot.subscriptions().get(state))
                .tag("status", state).register(registry);
        }
        Gauge.builder("covie.billing.entitled.merchants", this, metrics -> metrics.snapshot.entitled()).register(registry);
        Gauge.builder("covie.billing.reconciliation.pending", this, metrics -> metrics.snapshot.pending()).register(registry);
        Gauge.builder("covie.billing.reconciliation.review.required", this, metrics -> metrics.snapshot.reviewRequired()).register(registry);
        Gauge.builder("covie.billing.reconciliation.oldest.check.age.seconds", this, metrics -> metrics.snapshot.age()).register(registry);
        Gauge.builder("covie.billing.database.last.success.unixtime", this, metrics -> metrics.snapshot.lastSuccess()).register(registry);
        Gauge.builder("covie.billing.reconciliation.enabled", this, metrics -> metrics.reconciliationEnabled ? 1 : 0).register(registry);
        Gauge.builder("covie.billing.payment.creation.enabled", this, metrics -> metrics.gateway.isEnabled() ? 1 : 0).register(registry);
        failures = registry.counter("covie.billing.database.refresh.failures");
    }

    @Scheduled(fixedDelayString = "${covie.billing.metrics-refresh-ms:15000}")
    public void refresh() {
        try {
            Map<String, Double> invoices = counts("select status,count(*) total from billing_invoices group by status", INVOICE_STATES);
            Map<String, Double> subscriptions = counts("select status,count(*) total from merchant_subscriptions group by status", SUBSCRIPTION_STATES);
            Map<String, Object> pending = jdbc.queryForMap("""
                select count(distinct i.id) pending,
                    count(distinct i.id) filter(where obs.discrepancy<>'NONE') review_required,
                    greatest(0,coalesce(extract(epoch from (now()-min(coalesce(obs.last_checked_at,i.created_at)))),0)) age,
                    (select count(distinct merchant_id) from billing_entitlement_grants
                     where revoked_at is null and valid_from<=now() and valid_until>now()) entitled
                from billing_invoices i join billing_payment_attempts a on a.invoice_id=i.id
                left join billing_reconciliation_observations obs on obs.invoice_id=i.id
                where i.status='PENDING_PAYMENT' and a.provider='PAYOS' and i.payos_order_code is not null
                """);
            snapshot = new Snapshot(invoices, subscriptions, ((Number) pending.get("entitled")).doubleValue(),
                ((Number) pending.get("pending")).doubleValue(), ((Number) pending.get("review_required")).doubleValue(),
                ((Number) pending.get("age")).doubleValue(), Instant.now().getEpochSecond());
        } catch (RuntimeException failure) {
            snapshot = unavailable(snapshot.lastSuccess());
            if (failures != null) failures.increment();
        }
    }

    private Map<String, Double> counts(String sql, List<String> states) {
        Map<String, Double> values = new LinkedHashMap<>();
        states.forEach(state -> values.put(state, 0.0));
        jdbc.query(sql, result -> {
            String state = result.getString("status");
            if (values.containsKey(state)) values.put(state, result.getDouble("total"));
        });
        return Map.copyOf(values);
    }

    private static Snapshot unavailable(double lastSuccess) {
        Map<String, Double> invoices = new LinkedHashMap<>(), subscriptions = new LinkedHashMap<>();
        INVOICE_STATES.forEach(state -> invoices.put(state, Double.NaN));
        SUBSCRIPTION_STATES.forEach(state -> subscriptions.put(state, Double.NaN));
        return new Snapshot(Map.copyOf(invoices), Map.copyOf(subscriptions), Double.NaN, Double.NaN, Double.NaN, Double.NaN, lastSuccess);
    }
}
