# Seller Billing operational signals

## Scope and access

The existing private management Prometheus endpoint exposes cached Billing gauges and
finite-label operation timers. Keep the existing management network/authentication
topology: do not publish financial metrics through the public application ingress.
No merchant, invoice, order code, request body, payment URL, signature or secret is a
metric label. Dashboard provisioning uses the existing `Prometheus` datasource.

`BillingDatabaseMetrics` refreshes three aggregate SQL statements every 15 seconds
by default (`covie.billing.metrics-refresh-ms`). Statement count and result
cardinality are bounded; retained-table scan cost is not. Measure representative
query plans before changing refresh frequency or claiming production capacity.
Scraping reads the snapshot, not SQL. The three statements are operational samples,
not an atomic financial report: concurrent settlement may cross their boundaries.

## Metric map

Prometheus names are listed below (Micrometer names use dots).

| Metric | Meaning and operator interpretation |
|---|---|
| `covie_billing_invoices{status}` | Retained invoice rows by five fixed stored statuses; not an all-time creation counter or revenue total. `PENDING_PAYMENT` does not establish that a transfer occurred. |
| `covie_billing_subscriptions{status}` | Merchant subscriptions by eight fixed stored statuses. A stored `ACTIVE` value is not proof of current time-valid entitlement. |
| `covie_billing_entitled_merchants` | Distinct merchants with a current, unrevoked entitlement grant at refresh time. This is not an oracle for every commercial permission or merchant suspension decision. |
| `covie_billing_reconciliation_pending` | Distinct pending invoices with a PAYOS attempt and provider order code. Does not include every unpaid invoice or establish missing money. |
| `covie_billing_reconciliation_review_required` | Those pending invoices whose latest persisted observation has a non-`NONE` discrepancy, including `PROVIDER_FAILURE`. This remains visible when repeated failed checks update the last-check timestamp. It is current unresolved state, not a lifetime error counter. A verified clean retry or terminal invoice removes it. |
| `covie_billing_reconciliation_oldest_check_age_seconds` | Maximum elapsed age since last provider check, or invoice creation when never checked, among those pending invoices. Zero when none. This is not age since bank transfer. |
| `covie_billing_database_last_success_unixtime` | Last successful snapshot epoch seconds; zero before any successful refresh, retained after failures. |
| `covie_billing_database_refresh_failures_total` | Process counter of failed refreshes. Snapshot business gauges become `NaN`, not misleading zero, on failure. |
| `covie_billing_reconciliation_enabled` | Existing reconciliation worker configuration, 0/1. No activation or provider call. |
| `covie_billing_payment_creation_enabled` | Existing gateway `isEnabled()` capability, 0/1. Not provider reachability or permission to move money. |
| `covie_billing_operations_seconds_count`, `_sum`, `_max` | Finite operation/outcome timer series: checkout request completion/rejection/failure; webhook settled/duplicate/ignored/sample/other/rejected/failed; entitlement and entitlement_batch observed duration. Replays remain requests, not extra invoices or settlements. |

Webhook service timing is recorded outside the transactional service proxy: a
`settled` outcome follows successful commit. It does not replace ledger reconciliation.
Entitlement `observed` includes successful and exceptional evaluation duration and
does not mean access was allowed. Dashboard duration is a mean, not p95; no Billing
histogram percentile is claimed. No entitlement-result cache was added: evaluations
still consult existing repositories. Existing settings cache behavior is unchanged.

Reuse generic HTTP error/latency, rate-limit, outbox and scheduler signals. Existing
`covie_api_errors_total{code="BILLING_SUBSCRIPTION_REQUIRED"}` observes denials;
neither it nor shadow ALLOW/DENY/preview meters proves an erroneous denial without
independent expected-permission evidence. Do not fabricate stale-cache or false-denial
metrics from these values.

## Pilot alerts and response

The following thresholds are editable technical pilot defaults, not a provider SLA
or a new business policy. Alert labels are job/instance/severity only.

| Alert | Trigger | Response |
|---|---|---|
| `CoVieBillingMetricsUnavailable` | API up but snapshot missing, zero or at least 120 seconds old for 2 minutes | Check scrape target, database reachability and refresh failures. Treat financial gauges as unknown; do not treat absence as a healthy zero. |
| `CoVieBillingReconciliationChecksStale` | Worker enabled and pending check age above 1800 seconds for 5 minutes | Inspect scheduler/provider errors and authorized invoice state. Ordinary unpaid invoices are not confirmed lost transfers. Do not issue replacement invoices or grants from this alert. |
| `CoVieBillingReconciliationRequiresReview` | Worker enabled and pending invoices with persisted discrepancies or provider failures for 5 minutes | Inspect authorized reconciliation observations. Fresh failed checks do not make this signal healthy. Verify provider credentials/signatures/status without creating a new charge or granting access from the alert. |
| `CoVieBillingCheckoutFailures` | Creation enabled and a failed checkout request in a 5-minute window, persisting 1 minute | Inspect generic API errors/provider availability and recover the existing authorized invoice; never retry with invented amount or a new key to bypass uncertain state. Disabled creation intentionally stays quiet. |
| `CoVieBillingWebhookRejections` | More than 10 rejected callbacks in a 5-minute window, persisting 2 minutes | Inspect signature/configuration/routing and security logs without copying callback secrets. Verify old payments through authenticated provider reconciliation. This alert remains active when new payment creation is OFF. |

Intentional worker/creation OFF suppresses their corresponding operational alerts,
not snapshot health or webhook verification alerts. Keep normal money, release,
reconcile and enforcement flags OFF until their independent gates pass.

For an unverifiable legacy retry, preserve the approved safe `409`: create no new
invoice, link or entitlement. Use the existing authorized Seller invoice/payment
status read and operator reconciliation path. New fully bound same-key/body retries
retain successful replay; an alert is not authority to change that contract.

## Local verification and external delivery boundary

Run configured Prometheus 3.6.0 `promtool check rules` and `test rules` against
`infra/observability/prometheus/tests/*.test.yml`. Billing fixtures cover missing
snapshots, firing/recovery and intentional OFF guards. Parse the dashboard JSON and
evaluate its expressions with explicit synthetic/empty inputs. These are local
rule/expression checks, not proof of Grafana rendering or actual alert delivery.

The existing Alertmanager webhook receiver reads its destination from
`/run/covie-secrets/alert-webhook-url` and sends resolved notifications. The operator
must privately configure the real destination and verify actual firing and recovery
delivery before pilot activation. Do not put its URL/token in source or chat, invent
delivery evidence, or enable real payment to test notifications. Local scrape evidence
and dated execution logs belong in the Billing acceptance progress report.
