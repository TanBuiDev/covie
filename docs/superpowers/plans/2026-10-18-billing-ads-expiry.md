# Billing Ads Expiry Implementation Plan

> **For agentic workers:** Use executing-plans for this bounded chunk; existing read-only reviewer reviews the plan. No shared-source parallel edits or overlapping Maven.

**Goal:** Prove and repair original BIL030 delivery of merchant-linked advertisements after selling eligibility expires, without inventing compensation or changing campaign history.

**Architecture:** Reuse current server billing entitlement rules and existing delivery expiry contract. Apply the same current eligibility to seller campaign ownership and local merchant/item targets, including Admin-created campaigns. Pause distribution only, preserve campaign/invoice/financial records, require no renewal resubmission, and leave nonmerchant ads and enforcement-OFF behavior intact. Credit/refund remains separately governed by Ads contract/manual process; no new money movement.

**Tech Stack:** Java25/Spring/JdbcTemplate/PostgreSQL18, existing issued JWT/real HTTP fixtures, existing Next AdSlot expiry behavior.

## Chunk 1: Evidence and minimal repair

Implementation-review amendment: preserve the original lazy ten-delivery behavior when OFF; process ON candidates in bounded ten-row batches, stopping at ten successful deliveries, so the new billing batch must not eagerly expand existing destination/moderation reads to100 valid candidates. Resolve absolute destinations only when their scheme/host/effective-port match the configured canonical `covie.production.public-url` (existing COVIE_PUBLIC_URL), then apply the identical local merchant/item rule; genuinely external HTTPS links remain unchanged. Prove both with actual request SQL counter10vs20 candidates under OFF/ON and signed-in Admin same-origin absolute merchant/item URLs. Full65324 compiled before these test additions; collect terminal XML, run these new cases RED before production repair. No fabricated7-product-RED claim: initial seven includes three incorrect new If-Match fixture failures.

Files: existing `apps/api/src/test/java/vn/covie/billing/api/BillingAuthorizationHttpIntegrationTest.java`; `apps/api/src/main/java/vn/covie/ads/AdService.java`; policy record `docs/architecture/adr-seller-billing-ads-expiry.md`; existing Ads tests and `apps/web/features/ads/ad-slot.test.tsx` only if a specific missing expiry regression is confirmed.

- [ ] Add parameterized actual HTTP proof for Seller-owned merchant target, Admin merchant target and Admin item target. Create approved campaign through actual actor APIs; seed valid paid entitlement using existing checkout/signed callback, then use existing fixtureTerm to simulate grace/expired states. Retain full finance/campaign snapshots. Actual active GET must omit unavailable campaign, old delivery event422/no event insertion, draft submit409, while legacy read remains authorized. Observe actual RED before production edits.
- [ ] Add known-near-expiry fixture proving deliveryExpiresAt cannot exceed current active entitlement or campaign end; expired delivery is removed by existing frontend timer. Never extend expiry through grace. Confirm grants renewed before old expiry permit new delivery while not reviving an expired token. Default enforcement OFF retains delivery; unrelated Admin `/search` campaign remains eligible. Normal settings untouched outside disposable fixture.
- [ ] Implement minimal common eligibility checks for current campaign merchant and resolved local target merchant. Remove the SQL grace-selling OR branch only after RED. Reuse existing entitlement implementation, no schema/state/API-field change. Prefer bounded/batched eligibility if needed rather than per-row expansion of database requests. Bound emitted expiry by actual current entitlement and campaign end plus existing30minute token lifetime. Recheck eligibility for old events.
- [ ] Focused HTTP and old Ads tests GREEN; inspect public response compatibility and request SQL count if adding batch selection. Add only targeted browser/component expiry evidence when current contract timing requires it; no whole UI redesign.
- [ ] Record pause-only technical ADR within approved no-unpurchasable-offer scope; no assertions of an approved credit/refund policy. Update original BIL030 only when actual complete local proof exists; otherwise retain precise partial state.
- [ ] Sequential full Maven after final source, current opt-ins; save independent XML before later Maven. Reuse unchanged Web build when no Web production changes, run targeted existing Ads UI expiry behavior. Update AGENTS/report with exact results and remaining original025/028/security/operations/pilot gaps. No branch/commit/push/deploy/activation/recovered data.

Validation from `apps/api` with existing Java25/Maven configuration: `./mvnw.cmd -o '-Dtest=BillingAuthorizationHttpIntegrationTest#merchantLinkedAdsPauseAtBillingExpiryWithoutChangingCampaignOrFinance' test` (parameterized three actual target paths), then same plus `AdServiceIntegrationTest,AdServiceTest`. Near-expiry test name and actual timing details must be recorded before running; a fixture/setup failure is not a product RED.

Next action: Review plan and reproduce actual expired/grace delivery.
Required from you: Nothing
Completion condition: Evidence-backed pause of unavailable merchant offers, unchanged finance/history and OFF behavior; no invented Ads compensation.
