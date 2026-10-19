# Billing UI Local Acceptance Implementation Plan

> **For agentic workers:** Use executing-plans for this bounded chunk and the existing read-only plan reviewer. Never overlap Maven or a Next build with an owned native Next process. No commits/remote writes/activation.

**Goal:** Complete remaining original BIL025 using actual 360/390/1366 UI/state/keyboard evidence, fixing only confirmed accessibility or legibility defects.

**Architecture:** Preserve existing Admin/Seller business logic, permissions, design tokens and API clients. Actual current native Admin screenshots show finance table identifiers/statuses wrapping to individual character fragments; all four Billing tables use undefined `admin-table` styling rather than the existing `admin-data-table`/`admin-table-scroll` patterns. Reuse those existing shared classes and accessible named scroll regions, not a global redesign. Audit loading/error/denied/retry/resilience independently; do not infer complete a11y from no page overflow.

**Tech Stack:** React19/Next16.4, existing Vitest/testing-library, native Chrome155/Python Playwright and issued-JWT/owned API/PG fixture.

## Chunk 1: Proven table readability and UI state gaps

Read-only plan reviewer Approved2026-10-18: no concrete blocking defects, no edits/tests. Acceptance still requires every named original state, including retained Seller pending/success/error proofs. This is a next-chunk plan only; no UI production edits or new UI tests have been made while full92036 is running.

Files: existing `apps/web/features/admin/admin-billing-screen.tsx`; new `apps/web/features/admin/admin-billing-screen.test.tsx` (none currently exists); existing `apps/web/features/seller/seller-billing-panel.tsx`/`.test.tsx` only after actual missing-state proof; existing `apps/web/e2e/billing-local-reconciliation.py` for native finance table measurements/keyboard and denied states. Preserve existing canonical API/BE production code and normal E checkout `.next`.

- [ ] Collect terminal full92036 and independent all-suite XML BEFORE any subsequent Maven. It covers final Ads source; no source/build change while it uses built Next. Do not mark BIL030/whole goal from a live run.
- [ ] Add one focused Admin screen test with existing authorized GET response shapes: published plan/policy/overview/invoice/finance case/voucher/role assignment. Deferred startup must expose a status announcement; each populated table must have a uniquely labelled focusable scroll region, established shared table classes and scoped column headers. Actual new component RED before markup repair. Do not invent API error shapes or partial arrays.
- [ ] Replace only four existing table wrapper/class pairs with existing `admin-table-scroll` and `admin-data-table`; retain columns, text, IDs, actions and no-store endpoints. Give wrappers role=region, unique Vietnamese aria-label and tabIndex=0 so native keyboard scrolling works. Add scope=col to existing headers. No new colors/fonts/globalCSS/dependency, no truncating sensitive IDs into ambiguous shortened data, no authorization or financial business changes. Loading announcement uses existing role=status rather than new UI design.
- [ ] Observe existing actual error/retry/unauthorized and mid-form navigation behavior before deciding whether another correction is necessary. Do not replace working request/load logic just for this plan. Add focused missing-state tests only when original BIL025 lacks evidence; preserve dirty form fields and original pending payment/replay safety. Seller409/503 recovery and pending-disable proof are already retained on unchanged paths.
- [ ] Run targeted new/existing affected component tests/typecheck/lint. Actual production-mode native360/390/1366 fixture must assert table cell bounding geometry/text readability, no document overflow, unique labelled region and keyboard ArrowRight scrollLeft increase on mobile; desktop table remains readable. Screenshot table area at original resolution (not a tiny whole-page thumbnail), view every changed surface. Current reader/operator screenshots can seed diagnosis, not final acceptance of changed markup.
- [ ] Reuse current native manual-reconciliation and CSV/privacy workflow where unchanged; extend only named state/keyboard assertions rather than replay every payment scenario. Production Next rebuild required for actual UI edits, before its native fixture starts. Any Java fixture changes require focused native verification; BE production unchanged alone is not a reason to repeat full Maven.
- [ ] Run required Web full tests/typecheck/lint/build/client drift after final UI changes. Record exact counts/failures/skips, not sums from separate runs. Local keyboard/accessibility assertions are not physical screen-reader certification or field-device acceptance. Update original BIL025 only when its named states have concrete evidence; retain whole goal ACTIVE until028 and all security/operations/pilot requirements are resolved.

## Known independent remaining work

BIL028: current price publication emits BILLING_PRICE_PUBLISHED, but BillingNotificationHandler only supports reminder/settlement/expiry. This is a source gap, not a runtime RED yet. Reproduce missing notice through actual event dispatch before minimum correction; follow approved effective-date/nonretroactive policy and configured plan versions, never invent a founder discount/cutoff. Existing price/calendar/legacy grant tests remain valid within unchanged scope.

HotQuery opt-in, actual separate JVM restart, security and pilot preparation remain required where locally feasible; missing real PSP credentials/HTTPS/money approval are facts only at the genuine pilot gate.

Next action: Review bounded plan, collect full92036, then targeted UI RED and minimum markup/state fixes.
Required from you: Nothing
Completion condition: Actual original BIL025 states and readable keyboard-accessible finance tables, not only compilation or an empty screenshot.
