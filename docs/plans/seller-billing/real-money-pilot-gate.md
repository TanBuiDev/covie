# Real-money payOS pilot gate — 2026-10-16

This plan DOES NOT authorize a payment. payOS may charge real funds. No provider sandbox is assumed.

## Before a real payment

- Keep PAYOS_PAYMENT_ENABLED=false, COVIE_BILLING_RELEASE_APPROVED=false, billing.enforcement_enabled=false until the operator signs off. Maintain API-only secrets in the private local environment; never commit or expose them to the browser.
- Confirm all new PR CI checks are green and local migrations and signature verification have passed. Provision public HTTPS web origin and webhook proxy to POST /api/v1/billing/webhooks/payos.
- Admin should create a dedicated pilot-only plan/price revision rather than reduce the price for all existing merchants. Select the smallest **positive VND amount the actual payOS merchant channel supports**, after confirming provider minimums and any fees. There is no guarantee a 1 VND or below-threshold transaction is accepted. Do not bypass validations or use vouchers to evade provider limits.
- Show the final quote and invoice amount for explicit human confirmation; do not infer PAID from browser redirects.
- Only after explicit consent to send real funds, an operator can temporarily enable PAYOS_PAYMENT_ENABLED=true and COVIE_BILLING_RELEASE_APPROVED=true on an isolated pilot. The seller completes the hosted payOS payment manually. This assistant must never trigger the transfer.
- Verify a signed callback or authenticated signed provider GET, exact amount/currency, one PAID invoice and one entitlement grant. Retry webhook to verify idempotency; check no-webhook recovery and invalid signature failure. Never post credentials or bank details in GitHub.
- Return both payment flags to false, review ledger evidence, tax/invoice implications, and keep enforcement off. Do not merge or launch global billing until BIL-001–030 and local rollback rehearsal pass.

## Unresolved P0 gates

- Admin If-Match revision checks and server-bound expiring one-use quotes are implemented in source (V031/V032); full concurrent DB/provider verification remains outstanding.
- Original BIL IDs traceability, full crash/race E2E and real provider signature compatibility require evidence before broad production rollout.
- The original detailed approved Seller Billing v1.0 plan is stored at historical commit e976ce1af5de4928800b8c65108cdfad858fb4e8; the newer acceptance matrix reuses BIL IDs for different scenarios. Do not treat the newer matrix as a substitute for the original.
