# Seller Billing: pause unavailable merchant-linked Ads delivery

## Scope and authority

Original approved Billing v1.0 A05 is READ_ONLY grace, A10 proposes pausing merchant-linked distribution, and BIL030 forbids advertising an offer that cannot be purchased without notice. This record documents the minimal technical pause implementation within that scope. It does not claim approval of any new Ads credit/refund/contract terms or activate billing enforcement.

## Decision

When billing enforcement is enabled, current active, unrevoked entitlement is required for both a campaign's merchant and a local merchant/item destination. Admin-created campaigns with no campaign merchant cannot bypass destination eligibility. Grace does not permit new distribution or telemetry acceptance for an unavailable offer. Existing merchant/item moderation checks remain in force.

Pause distribution, not campaign history: no campaign status, dates, paid invoice, grant or financial rows are changed by eligibility reads. A qualifying renewal allows new delivery without forced resubmission of an unchanged approved campaign. Unrelated nonmerchant destinations and enforcement-OFF preserve their existing behavior.

Delivery expiry is the earliest of the existing30minute lifetime, campaign end and each applicable current entitlement's expiry. The existing public deliveryExpiresAt field and frontend removal timer enforce known expiry without waiting for the60second refresh. Event acceptance independently rechecks current eligibility and the delivery token; renewal does not extend an already-expired token.

Eligibility uses the same active-grant predicate as BillingEntitlementService. ON checks ten-candidate batches and stops after ten deliverable cards; OFF retains original lazy destination reads. Actual request SQL counter regressions cover ten versus twenty valid candidates. Local classification includes relative and canonical-origin absolute merchant/item URLs (scheme/host/effective-port match) and normalizes literal/encoded dot segments as browsers do. Genuinely external HTTPS destinations remain separate. No new database schema, REST field, payment action or campaign mutation is introduced.

## Limits

Immediate client-side removal on an unscheduled revocation still depends on a subsequent read; server-side new delivery/event acceptance rechecks eligibility. No claim of real-time push invalidation, credit, compensation, external advertising contract acceptance, staging or production deployment is made. Any compensation follows the separate approved Ads contract/manual finance process, not an automatic subscription-side adjustment.

Actual HTTP, component and native-browser evidence and remaining acceptance are recorded in the associated local progress report. Test source alone is not proof.
