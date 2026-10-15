# Seller Billing — Implementation checkpoint (2026-10-15)

**Branch:** \`feat/seller-billing-subscription\`. **Status:** PARTIAL, NOT TESTED. Billing enforcement defaults OFF and payment is not connected. No live charges, no deployment.

## Implemented on branch
- Flyway \`V020__seller_billing_foundation.sql\`: versioned price/policy, subscriptions, invoice, payment attempts, entitlement grants, webhook inbox, 10,000 VND initial published price.
- \`BillingCatalogService\`: read current published plan/policy, publish next immutable price/policy version with Admin authorization, audit/outbox. Future version must have future effective time.
- \`BillingEntitlementService\`: server-side eligibility/read-only evaluation from grant table, all shops allowed while enforcement is off.
- Read API: \`GET /api/v1/billing/plans\`, seller-scoped billing summary, Admin policy, Admin publish version API. Public plan route opened at security config.
- System flags: \`billing.enforcement_enabled=false\`, \`billing.seller_registration_enabled=true\`, \`billing.reminders_enabled=false\`.
- Deployment approval guard: turning on \`billing.enforcement_enabled\` needs \`COVIE_BILLING_RELEASE_APPROVED=true\` in server environment; do **not** configure until acceptance complete.
- Unit test source for month-end date arithmetic.

## NOT implemented; mandatory before marking complete
- Invoice issue/renewal transactional service; quote TTL and idempotency; provider payment intent/QR; webhook authentication and durable reconciliation; refunds/ledger; schedulers and notifications.
- Seller UI / Admin UI / generated OpenAPI client. These require exact Figma production frame and project UI instructions.
- Merchant write-side enforcement across catalog, checkout at commit, discovery/Need/ads, existing merchant grants and migration.
- Admin fine-grained billing permissions (currently legacy Admin role).
- End-to-end tests, local Java 25/Maven build, PostgreSQL Flyway migration tests, browser tests, sandbox verification, legal/PSP selection.

## Known technical checks for next implementation session
1. Obtain current branch/PR SHA, re-read root/closest AGENTS & RULES, detect upstream changes and verify SQL migration order.
2. Run Java 25 Maven compile/Flyway against a disposable PostgreSQL DB; resolve any errors with actual logs.
3. Prioritize invoice/settlement correctness, idempotency and state transitions before accepting real funds.
4. Do not enable enforcement or payment until seller legacy migration and consumer behavior are safe.
5. Do not interpret this PR as release-ready.

**Prohibition:** Do not merge this branch or enable collection of real money without review, tests and PSP contract/security verification.
