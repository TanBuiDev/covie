# Seller Billing legacy checkout retry — owner decision 2026-10-18

Status: approved by owner in this chat on2026-10-18. Applies to original BIL008 and existing invoice retries; no real-money activation or rollout approval implied.

## Evidence

Historical checkout fingerprints stored only normalized plan/voucher. They cannot prove the entire original body, including quoteId and optional expected amount/revision. Quote retention also removes expired tokens. Reconstructing or accepting the first newly supplied body is not proof of the original request.

The current versioned fingerprint binds the complete supported checkout request. Matching current requests can safely return the existing invoice/payment; changed requests conflict. Existing financial records must not be rewritten or regenerated to manufacture missing historical evidence.

## Decision

Fail closed with HTTP409 when a legacy fingerprint cannot verify the complete original body. Do not create an invoice, payment link or entitlement, and do not call the payment provider again. Return the existing invoice ID and its merchant-scoped status path in the existing error details. Seller continues through authorized read-only invoice/status endpoints, not a new checkout or a fabricated payment-success state.

Retain successful replay for current fingerprints when the same Idempotency-Key and complete supported body match, including after quote consumption/expiry. Different current bodies conflict. Existing OWNER authorization and cross-merchant isolation apply before any replay/status disclosure.

Owner explicitly selected safe409 over historical replay compatibility and requested regression tests for both old and current requests. This supersedes earlier WIP acceptance of weak historical-plan fingerprint replay. No schema migration, data deletion/backfill, provider charge, automatic refund or entitlement rewrite is authorized by this decision.

## Verification required

Actual concurrent authenticated HTTP on owned PostgreSQL demonstrates one invoice/attempt/provider create, safe intermediate conflict or exact replay, and changed-body rejection. Legacy retry409 leaves financial rows/provider-call counts unchanged; authorized status reads recover the original payment information. Seller UI recovers existing pending state after409 without another checkout. Focused RED/GREEN and subsequent production-source full verify must be recorded; source existence is not acceptance.
