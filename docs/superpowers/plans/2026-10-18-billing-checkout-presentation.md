# Seller Checkout QR, Reference and Expiry Implementation Plan

> **For agentic workers:** Execute in the existing isolated Billing worktree with targeted RED/GREEN and read-only reviewer. No commit/branch/remote write or real payment.

**Goal:** Implement the explicitly missing original section9.2 QR/reference-copy/countdown presentation without changing financial behavior or existing API fields.

**Architecture:** Use the existing signed-provider `CheckoutResult.qrPayload`, existing invoiceNumber/orderCode and server expiry timestamps. Render QR entirely locally with the existing hosted payOS link retained. On reload, existing authorized status recovery still opens the same hosted payOS page; do not manufacture a QR when the status DTO does not contain one. Countdown is advisory display, never payment verification or quota release. Backend remains authoritative.

**Tech Stack:** Current React/Next, generated CheckoutResult/PaymentStatus types, one pinned local QR encoder dependency `qrcode@1.5.4` plus `@types/qrcode@1.5.6`, Vitest and existing owned native fake-provider fixture. Official encoder documentation: https://github.com/soldair/node-qrcode . No external QR generation/image service, no new analytics/logging/storage of payload or payment key.

## Chunk 1: Focused UI implementation

### Task 1: QR and invoice reference

Files: create `apps/web/features/seller/billing-payment-details.tsx` and focused test; integrate only in `seller-billing-panel.tsx`; dependency manifest/lockfile changes only for encoder/types.

- [ ] Unit RED for supplied current-invoice QR appearing, native clipboard copy success/failure, unavailable QR fallback, paid/expired state hiding stale QR. Test mismatched old checkout invoice must never render its QR for a newer pending invoice.
- [ ] Local QR encoder Promise → dataURL, existing accessible image presentation with quiet zone/high contrast; cleanup ignores stale results and catches encoding failure. No fetched QR URL, unsafe SVG injection, hydration invented image or private payload in logs/URLs/storage. Preserve hosted link and no duplicate checkout request.
- [ ] Reference is explicitly an invoice/support reference, not a fabricated bank-transfer description: use actual invoiceNumber or matching current checkout orderCode with existing CB- invoice convention. Copy only exact displayed reference; advise using original provider QR/link rather than editing bank-transfer content. Clipboard rejection remains retryable, does not claim copied.
- [ ] QR only comes from matching retained CheckoutResult. Existing recovered status has no QR field: show clear hosted-payOS QR fallback, retain same invoice link; do not expand status privacy whitelist/contracts solely for rendering.

### Task 2: Countdown

Files: create small `billing-expiry.tsx` + focused test or keep one small component with labelled advisory countdown; integrate quote/current link timestamps into existing panel.

- [ ] RED quote/link before-boundary, exact expired-boundary and server-PAID cases using clock-controlled existing timestamps.
- [ ] Tick remaining minutes/seconds, clear interval/unmount, no assertive per-second live announcements. Announce expiry once and direct user to refresh/check actual server state. Do not declare invoice VOID, free voucher/quota, create replacement automatically or infer success from clock/redirect/QR.
- [ ] Existing quote/payment parents use role=status: ticking subtree must explicitly be aria-live=off, with a separate once-only expiry announcement. Verify actual ancestor nesting rather than assume a non-live child by default.
- [ ] Existing quote createCheckout server validation stays unchanged. Unknown/invalid timestamp cannot invent validity. Existing pending invoice continues to block duplicate checkout even when displayed timer expires.
- [ ] Focused component+panel tests, typecheck/lint, current dependency audit and lockfile inspection. No broad dependency upgrade or unrelated fixes.

## Chunk 2: Native and original-plan acceptance

- [ ] Build final current Web only after contract/client consistency; native actual Seller checkout at360/390/1366 with isolated signed loopback provider verifies local QR image, exact reference copy and quote/link countdown, no remote QR requests, same pending invoice after reload/back and server settlement only after signed verification.
- [ ] Independently decode screenshot QR against actual fake-provider opaque payload using a local test-only decoder if available; do not claim actual bank-valid VietQR or real PSP acceptance from a fixture payload.
- [ ] Review actual screenshots/focus/overflow and accessibility semantics; no Figma redesign (owner explicitly waived matching old Figma).
- [ ] Update whole section9.2/BIL025 traceability truthfully. Onboarding stepper/terms, days remaining, broader security/operations/JVM restart/pilot acceptance remain separate unfinished approved work; no whole-goal completion from these widgets.

Next action: Reviewer checks privacy/no-contract-expansion, clock semantics and native evidence; execute focused missing presentation tests.
Required from you: Nothing
Completion condition: Original checkout QR/reference/countdown works safely with existing financial contracts and evidence, without hiding other local requirements.
