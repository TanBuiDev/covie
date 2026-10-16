package vn.covie.billing.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disabled until explicitly enabled by ops. No live provider calls by default. */
@Component
@ConditionalOnProperty(name="COVIE_BILLING_RECONCILE_ENABLED", havingValue="true")
public class BillingReconciliationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(BillingReconciliationScheduler.class);
    private final BillingReconciliationService service;

    public BillingReconciliationScheduler(BillingReconciliationService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString="${COVIE_BILLING_RECONCILE_INITIAL_MS:60000}",
               fixedDelayString="${COVIE_BILLING_RECONCILE_DELAY_MS:900000}")
    public void run() {
        // Bounded batch, provider may rate-limit; never call remote service inside DB transaction.
        for (var candidate : service.pending(20)) {
            try {
                var observation = service.queryProvider(candidate);
                service.record(observation);
                if (!"NONE".equals(observation.discrepancy())) {
                    LOG.warn("Seller payment reconciliation requires review: invoiceId={}, discrepancy={}",
                        observation.invoiceId(), observation.discrepancy());
                }
            } catch (RuntimeException ex) {
                LOG.warn("Seller payment reconciliation candidate failed; invoiceId={}", candidate.invoiceId());
            }
        }
    }
}
