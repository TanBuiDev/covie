# Billing Observability Implementation Plan

> **For agentic workers:** Execute in existing isolated Billing worktree with targeted RED/GREEN and read-only review. No activation, remote write/commit or unrelated refactor.

**Goal:** Fill the confirmed original section15/G5 Billing metrics/alert gap without altering transaction, pricing, API or authorization behavior.

**Architecture:** Reuse Micrometer, existing operational metrics conventions and Prometheus. Durable invoice/subscription/reconciliation gauges come from scheduled cached bounded aggregate queries, never a database query per scrape/request. Operational request/outcome timers/counters use finite labels at existing controller/service boundaries, not merchant/invoice/provider-reference/PII labels. Existing generic API errors/HTTP latency/rate-limit/outbox/shadow metrics remain reused rather than duplicated.

**Tech Stack:** Existing Spring JDBC/Micrometer/Prometheus/Grafana, JUnit/Testcontainers and cached official promtool. No dependency/schema/client changes.

## Chunk 1: Durable gauges

Create small `apps/api/src/main/java/vn/covie/billing/observability/BillingDatabaseMetrics.java` using existing MeterBinder/scheduled-snapshot pattern. Add focused test in existing real HTTP billing suite (or one narrowly scoped real PG test if necessary).

- [ ] Actual RED scrape asserts missing billing gauge names before implementation. Final real PostgreSQL fixture seeds known states/ages and calls refresh, validates actual scrape counts and independent SQL expectations; prove scrape itself causes zero SQL, not a claim of no DB writes for authenticated requests.
- [ ] Gauges: retained invoices by fixed status (sum=created retained ledger count; PENDING_PAYMENT=unpaid), subscriptions by fixed stored status (merchant primary key means distinct merchants), current valid/unrevoked entitlement merchant count, pending provider-bound reconciliation count and oldest time since last check (creation if never observed), latest successful refresh timestamp. Name/document stored state vs time-valid entitlement correctly; no guessed paid status or transfer date.
- [ ] One/few bounded aggregate statements per configured refresh (default follows existing15s pattern); snapshot initiallyNaN until successful DB read, failuresNaN with fixed failure counter and last-success retained. No IDs/keys in labels/logs, no per-request/scrape SQL, no speculative index/migration changes.
  Bounded means statement count/result cardinality only; aggregate table-scan cost is not bounded independently of retained ledger volume. Measure actual seeded query plans before claiming scale/performance.
- [ ] Counts are DB gauges, not all-time process event counters. Do not count a callback retry as a new invoice/payment. Retention/transaction behavior unchanged.

## Chunk 2: Operation outcomes

Create `BillingOperationMetrics` with small finite timer/counter recording methods. Modify only the existing webhook/checkout controller boundaries and entitlement timing boundary as required, preserving existing direct-constructor tests/API signatures via appropriate compatible injection.

- [ ] RED actual signed webhook settled/duplicate/invalid-signature requests show missing outcomes; checkout success/failure and entitlement timing assertions tied to real existing calls. Avoid dozens of invented cases.
- [ ] Record webhook service elapsed time and finite returned outcome (settled/duplicate/ignored/sample/rejected/failed); controller is outside the service transaction proxy, so successful outcome means commit completed. Existing HTTP metrics still measure transport/HTTP status. Never parse/log raw webhook data for metrics.
- [ ] Checkout request success/failure measures intent request, not a bank settlement; authorized idempotent replay is still a request, not another invoice. Entitlement evaluation duration excludes client clock and leaves decision logic unchanged. Existing `covie.api.errors{code=BILLING_SUBSCRIPTION_REQUIRED}` represents observed denials, **not an oracle for erroneous denials**.
- [ ] Reuse existing shadow ALLOW/DENY/preview and rate-limit meters. Explicitly document no entitlement-result cache (DB read on evaluation); do not invent a stale-cache gauge or claim a true false-denial oracle without comparison evidence. Existing settings cache semantics remain unchanged.
- [ ] Observability failures cannot turn a valid financial operation into a failed API response; finite best-effort recording without sensitive exception/request data. Targeted tests prove this boundary if a new wrapper is required.

## Chunk 3: Rules, dashboard and actual acceptance

Modify `infra/observability/prometheus/rules/covie.rules.yml`, add narrowly scoped rule test and Billing dashboard/panels following existing provisioning; add `docs/runbooks/seller-billing-observability.md`.

- [ ] Add missing-refresh/backlog-age/observed-rejection or failed-intent alert rules with explicitly illustrative/configurable pilot thresholds, not invented provider/business SLA. No alert that treats ordinary unpaid pending invoices as confirmed lost bank transfers.
  Add two configuration-only gauges: reconciliation worker enabled and gateway payment-creation capability (actual existing isEnabled guard, not provider health). Gate backlog-age/failed-checkout rules when intentionally paused/OFF; do not gate rejected webhooks on creation because old-payment callbacks still need verification after payment creation is disabled. No provider calls or secret labels from these gauges.
- [ ] Run actual configured promtool check/unit rule tests, including firing/recovery and missing-gauge semantics. These are synthetic rule tests, not real Alertmanager/channel delivery.
- [ ] Actual current API PG/Redis/local fixture scrape shows new metrics0→known seeded/request-driven values with normal flagsOFF. Keep management binding private under existing topology; no new public financial data endpoint. Record labels contain no IDs/secrets.
- [ ] Targeted tests, full BE once relevant new production code stabilizes, canonical OpenAPI/client drift check; unchanged UI full539/native evidence reused unless touched. Rebuild/reverify packaged JAR only once after new production source stabilizes; prior JVM evidence becomes historical artifact provenance, not current-JAR proof.
- [ ] Whole-plan map explains explicit external channels/legal/provider/production gates; do not stop at30original rows or metrics alone.

Next action: Read-only reviewer checks metric semantics/transaction boundaries, cardinality and targeted test strategy before execution.
Required from you: Nothing
Completion condition: Required Billing operational signals have real local scrape and configured rule evidence, finance behavior unchanged and remaining whole-plan gates honestly tracked.
