# Billing Onboarding and Remaining Days Implementation Plan

> **For agentic workers:** Execute in the existing isolated Billing worktree using executing-plans, targeted RED/GREEN and read-only plan review. No commit, remote write or payment activation.

**Goal:** Complete original section9.2 onboarding progress/disclosure acknowledgement and advisory days remaining without inventing legal terms or changing registration/entitlement rules.

**Architecture:** Reuse MerchantForm validation/live preview and existing server onboarding policy; add the missing acknowledgement inside the actual new-shop form, not as an unconnected checkbox outside it. Existing server checks remain authoritative. Display remaining time from the existing server expiry; never infer allowedActions from a client clock.

**Tech Stack:** Existing Next/React, generated API schema, Vitest and the owned native billing fixture. No new dependencies or schema changes.

## Chunk 1: Onboarding disclosure

Files: `apps/web/features/merchant/merchant-form.tsx`, `apps/web/features/seller/seller-billing-onboarding.tsx`, `apps/web/app/(seller)/seller/page.tsx`, corresponding existing tests.

- [ ] RED required acknowledgement in billingOnboarding new-shop form, no acknowledgement in existing edit/non-billing contexts; no create POST before checking, keyboard-compatible input.
- [ ] Move the existing SellerBillingOnboarding into the billingOnboarding new-shop form so disclosure and consent share the submission context. Remove only duplicated external instances on /seller. Keep ordinary MerchantForm behavior unchanged.
- [ ] Checkbox acknowledges the displayed subscription conditions (server pricing/cycle, quote confirmation, no automatic renewal charge, verified server confirmation), not absent legal documents, VAT or provider approval. Mark it required, use controlled consent to guard submit too, no persisted or invented legal acceptance record. Explicitly retain legal/provider real-money pilot gates.
- [ ] Existing policy loading/failure cannot claim consent to unknown policy. Expose a ready callback from the policy component for this form only; disable acknowledgement while loading/failed and offer GET-only retry. Registration-disabled server policy keeps form submission disabled. Do not add a new registration ban when enforcement is OFF; use registrationEnabled as supplied.
- [ ] Small ordered progress list: profile → review price/cycle/conditions → payment QR when required by policy → server verification. Do not claim a completed payment or force a trial/free user into checkout. Preserve existing payment-only navigation rules and multi-shop selector.
- [ ] Focused component/form RED/GREEN, typecheck/lint; no shared CSS redesign or duplicate shop preview.

## Chunk 2: Remaining days and delayed confirmation

Files: small component alongside `billing-expiry.tsx` (or focused extension) and `seller-billing-panel.tsx`/tests.

- [ ] RED future/boundary/past/invalid expiry and independent server permission; advisory days only, no ACTIVE inference. Use clearly labelled ceil remaining24h periods, not guessed calendar/billing duration. Zero past expiry; no negative day values.
- [ ] Add display next to existing server expiry: eligibility expiry or actual subscription.periodEnd when enforcementOFF intentionally omits eligibility expiry. Never fabricate days from status alone. Time display never changes eligible actions or causes renewal/checkout. Regression verifies future periodEnd does not override server canSell=false.
- [ ] Explicit pending message explains signed server verification may be delayed and directs to existing refresh/invoice reference for support. No arbitrary support SLA/contact, redirect success or activated claim from payment state alone.
- [ ] Focused tests, final production build/full Web and owned native360/390/1366 checks for new presentation; unchanged BE full1057 remains valid unless backend source changes.

## Chunk 3: Evidence

- [ ] Review actual current screenshots and native checkbox keyboard/validation/GET retry; no invented physical screen-reader/legal acceptance.
- [ ] Update original BIL025 and whole9.2 mapping without marking wider restart/security/pilot work complete. Keep all normal money/enforcement/release flags OFF, preserve E worktree/recovered data.

Next action: Read-only reviewer checks disclosure scope, policy failure and server-owned permissions before execution.
Required from you: Nothing
Completion condition: Approved missing presentation requirements implemented with focused/native evidence, no financial or legal-policy invention.
