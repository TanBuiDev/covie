# Billing Manual Reconciliation Implementation Plan

> **For agentic workers:** Use executing-plans and targeted TDD. Preserve the full Seller Billing goal; this is one missing P0 capability, not final acceptance.

**Goal:** Implement the missing audited, idempotent Admin recheck required by original plan sections 7.2/10 and BIL021.

**Architecture:** Reuse authenticated, signed provider GET and existing settlement. Reserve an idempotency operation in a short transaction, perform provider I/O outside a transaction, then persist the result and completion audit in another short transaction. Terminal invoices are inspected without financial mutations. No real provider calls or feature activation during local acceptance.

**Tech Stack:** Java 25, Spring JDBC/transactions, PostgreSQL, generated OpenAPI/client, existing Next Admin screen.

## Contract and invariants

- Add POST `/api/v1/admin/billing/invoices/{invoiceId}/reconciliation`. The original endpoint was explicitly proposed; invoice IDs are already the Admin ledger identity. No existing route changes.
- Require current global Admin plus existing finance OPERATOR/APPROVER policy; fine roles OFF retains current global Admin fallback. Check authorization before cached replay.
- Require reason (nonblank, maximum 400 characters) and Idempotency-Key. Return operationId, invoiceId, invoiceStatus, providerStatus, discrepancy and checkedAt; no provider payload/secrets. Responses no-store.
- Bind key to actor/invoice/full request. Changed request409; identical completed replay returns original result without another lookup/audit. Active reservation409; interrupted operation retryable after technical lease30s, longer than provider12s timeout. Existing idempotency TTL24h retained.
- Commit REQUESTED audit before any provider call. Complete audit and idempotency result atomically. Never use generic IdempotencyExecutor around provider I/O: its transaction spans the command.
- Pending invoices may use existing signed recovery/void/link repair; terminal invoices are read-only. No unconditional PAID, charge, refund, grant or invoice creation.
- Report uncertain/invalid provider observations, never infer received money. Retain validated reason in the restricted REQUESTED audit so the operator justification remains reviewable, never in application logs or provider payload. Operators must not supply secrets or bank details. Fingerprint binds the full request.

## Chunk 1: Backend

Files: new `apps/api/src/main/java/vn/covie/billing/application/AdminBillingReconciliationService.java`, new `apps/api/src/main/java/vn/covie/billing/api/AdminBillingReconciliationController.java`; modify existing `billing/reconciliation/BillingReconciliationService.java`; tests in existing `BillingAuthorizationHttpIntegrationTest.java`.

- [x] Add actual HTTP regression for absent route, operator permission, signed missing-webhook recovery, replay, changed reason and audit.
- [x] Run focused Maven test and retain meaningful RED.
- [x] Implement short claim/completion transactions using existing idempotency table; reuse signed inspector and pending recovery.
- [x] Verify focused real PostgreSQL/issued-JWT/fake-provider tests; extend terminal-read-only, audit rollback and concurrent lease/network assertions.

## Chunk 2: Contract and Admin UI

- [x] Add server-owned RECONCILE allowedActions to Admin invoice list, computed once per request, not per row.
- [x] Export OpenAPI and regenerate typed client using official scripts; verify additive semantic drift only.
- [x] Add targeted UI RED; implement selection/reason/retry/result on existing Admin billing screen, generated types and stable retry key, no redesign.
- [x] Run affected UI tests/typecheck/lint/build and actual local fake-provider browser acceptance at360/390/1366, keyboard/error/retry/read-only Reader.

## Chunk 3: Acceptance

- [ ] Run wider gates after source stabilizes, save exact executed/pass/fail/skipped counts and inspect sole opt-in skip.
- [x] Update finance runbook, original BIL matrix and AGENTS with exact evidence and remaining whole-goal gaps.
- [ ] Preserve primary dirty checkout/data; keep normal payment/release/reconcile/enforcement flags OFF. No commit/push/merge/deploy.
