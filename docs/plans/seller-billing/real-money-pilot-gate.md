# Real-money payOS pilot gate — updated 2026-10-19

This plan DOES NOT authorize a payment. payOS may charge real funds. No provider sandbox is assumed.

Local implementation and verification are closed by
`local-final-acceptance-2026-10-19.md`; earlier local ACTIVE snapshots are historical.
This does not make the real pilot ready. Actual account/channel minimum/fees,
signature compatibility, HTTPS delivery, legal/VAT/invoice/terms, on-call/alert channel
and separate human money authorization remain required. Keep all normal flags OFF.

## Before a real payment

- Keep PAYOS_PAYMENT_ENABLED=false, COVIE_BILLING_RELEASE_APPROVED=false, billing.enforcement_enabled=false until the operator signs off. Maintain API-only secrets in the private local environment; never commit or expose them to the browser.
- Confirm all new PR CI checks are green and local migrations and signature verification have passed. Provision public HTTPS web origin and webhook proxy to POST /api/v1/billing/webhooks/payos.
- Admin should create a dedicated pilot-only plan/price revision rather than reduce the price for all existing merchants. Select the smallest **positive VND amount the actual payOS merchant channel supports**, after confirming provider minimums and any fees. There is no guarantee a 1 VND or below-threshold transaction is accepted. Do not bypass validations or use vouchers to evade provider limits.
- Show the final quote and invoice amount for explicit human confirmation; do not infer PAID from browser redirects.
- Only after explicit consent to send real funds, an operator can temporarily enable PAYOS_PAYMENT_ENABLED=true and COVIE_BILLING_RELEASE_APPROVED=true on an isolated pilot. The seller completes the hosted payOS payment manually. This assistant must never trigger the transfer.
- Verify a signed callback or authenticated signed provider GET, exact amount/currency, one PAID invoice and one entitlement grant. Retry webhook to verify idempotency; check no-webhook recovery and invalid signature failure. Never post credentials or bank details in GitHub.
- Return both payment flags to false, review ledger evidence, tax/invoice implications, and keep enforcement off. Do not merge or launch global billing until BIL-001–030 and local rollback rehearsal pass.

## Local evidence and unresolved activation gates

- Admin If-Match concurrency, server-bound quote/idempotent checkout, signed callback/reconciliation races, database recovery, backup/restore and original BIL001–030 traceability have actual local evidence. Use `acceptance-original-bil-v1-local-2026-10-17.md`, not the older remapped `acceptance-matrix-v1.md`.
- The older packaged-JVM crash/restart proof is bound to its recorded artifact. New dependency security patches and operational metrics require the final current package/full-backend/security/JVM evidence tracked in `local-security-observability-progress-2026-10-19.md`. Do not reuse the old artifact as current proof. Whole goal remains ACTIVE / NOT_READY while local work is open.
- Configured Prometheus rules and synthetic expressions are not real alert-channel delivery. Privately provision and verify the destination and operator response from `../../runbooks/seller-billing-observability.md` before activation.
- Confirm actual provider account/channel eligibility, fees/minimum, TLS callback delivery, legal/VAT/invoice responsibility, seller terms/privacy, on-call/support and explicit human money approval. None is supplied merely by passing fake-provider tests.
- Keep deployment/cohort/enforcement approval distinct from local completion. No global launch or old-merchant cutoff is implied by this document, and no real provider signature compatibility is claimed without actual authorized channel evidence.
