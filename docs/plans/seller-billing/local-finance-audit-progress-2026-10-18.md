# Seller Billing — finance permissions, manual cases and lifecycle audit

Whole original Seller Billing/payOS goal ACTIVE / NOT_READY. Original BIL022 is locally accepted for the approved manual P0 workflow, not a bank/provider refund. BIL021 remains partial because Admin manual reconciliation API/UI is genuinely missing. No transfer, real PSP, remote write, commit, branch, merge, deployment, normal-stack restart or recovered-data operation; normal flags stay OFF.

## Previous goal turn and resumed full gate

Previous turn made actual progress: measured/repaired Need/Detail N+1 and ran76focused cases. After user input, the original full handle30220 was missing and authoritative process/container/port inventory showed nothing live;14source hashes matched. The old log has no terminal BUILD result, so it remains interrupted/unproven, not a false pass.

Resumed full12819 TERMINALexit0 `.local/billing-query-count-full-verify-resumed.log`:1014reported/1013PASS/zero failures/errors/1HotQuerySKIP,8m12s. Independent `.local/billing-query-count-full-verify-resumed-summary.json` and `.local/billing-query-count-full-http.xml` preserve output before narrow runs. Billing HTTP34cases pass, including request SQL counts Search2/2,Shop3/3,Need13/13,Detail15/15. Native `.local/billing-browser-1792281823757/result.json` completed=true/ICT=true/pageErrors0. Thirteen manifest files excluding the newly extended HTTP test still match `.local/billing-query-count-post-full-source-check.json`. Subsequent finance methods/properties are not retroactively included in1014; these full results predate the audit changes below.

Fresh `git fetch --prune origin` succeeds at07:15ICT; feature/checkout HEAD remains41abfce04276950db2b680d44cd1b6daad0106e3,mainbb2e06b41282c8e9205c35a13e7b6adec68ceaa2. No checkout/reset/merge/push. All384original dirty-file hashes remain identical in `.local/billing-finance-original-preservation.json`. The owner's updated instructions declare Social local acceptance complete; no Social work is resumed here.

## Meaningful failures versus fixture corrections

Three new actual issued-JWT/HTTP/PostGIS/Spring cases use the existing owned HTTP fake provider. Only the isolated test fixture enables FINANCE_ROLE_ASSIGNMENT/ADMIN_GRANTS/REFUND_CONFIRM approval properties; normal backend configuration/defaults are not changed and no actual external refund occurs.

- `.local/billing-finance-http-red.log`:two cases,one genuine audit failure plus one fixture cleanup error,30.651seconds. REQUESTED case exists but its required audit count is0,not1. Cleanup incorrectly tried deleting a System Setting referenced by immutable history; PostgreSQL correctly rejects it. No product constraint is removed.
- Fixture cleanup now resets only the private finance flag value to false, retaining its row/history. Enable helper uses actual Admin GET/PATCH and proper If-Match. The first corrected helper used billing-price numeric ETag syntax rather than System Setting `"vN"`; `.local/billing-finance-http-confirmed-red.log` two failures/zero errors,29.080seconds, includes this harness400 plus unchanged audit0.
- Correct System Setting VersionPrecondition formatting then yields `.local/billing-finance-lifecycle-confirmed-red.log`:three cases/onePASS/two genuineFAIL/zero errors/skips,29.505seconds. Fine-grained finance permissions already work. Both refund REQUESTED and actual lifecycle EXPIRED lack their required audit records.

## Minimal audit correction

- `BillingFinanceCaseService.create` writes REQUESTED audit in the existing transaction, with actual OWNER/request correlation and minimal merchant/invoice/kind metadata. Reason and bank details are not copied to audit. Existing duplicate-case return occurs before insertion/audit, preserving one logical request record.
- Existing lifecycle status UPDATE/policy/notification behavior stays unchanged. Only rows returned by an actual state transition produce an audit; repeated no-change refresh does not. A generated per-run `billing-lifecycle-...` correlation and explicit scheduler source identify automated work independently of reminder notifications.
- The initial generic audit classified this background event as ANONYMOUS. New real-PG assertion confirms the defect in `.local/billing-lifecycle-system-actor-red.log`:oneFAIL/zero errors/skips,29.426seconds,expectedSYSTEM/actualANONYMOUS. Additive `AuditLogEntity.system`/`AuditLogWriter.systemEvent` sets SYSTEM and uses MANDATORY propagation; old securityEvent/securityFailure behavior is untouched. V002 already permits this actor string,so no migration or API DTO change.

No financial rule, price, approval flag default, existing authorization, API field, generated client, frontend or transfer mechanism changes. Audit remains part of the same transaction,not an independent commit.

## Exact focused results

`.local/billing-finance-lifecycle-green.log` TERMINALexit0:11PASS/zero failures/errors/skips,33.505seconds. Three new HTTP/PG cases pass (20.08seconds) plus four FinanceAccess,three FinanceCase and one IndependentRefund unit cases. This precedes only the SYSTEM classification refinement.

Actual new cases prove:

- Real Admin API assigns READER/OPERATOR/APPROVER with one audit per assignment; self-assignment and non-Admin target denied422. Finance READER reads but cannot grant/review/confirm; OPERATOR reviews but cannot grant/confirm; APPROVER grants. Global-Admin pricing permission remains separate. Revocation is audited and immediately rejects the same existing JWT.
- PostgreSQL audit-failure trigger makes a manual grant request500; full six-table financial snapshots and grant-outbox count remain unchanged. No logging bypass, fake success or transaction split is used.
- Actual OWNER (also Admin) opens/replays one refund case; requester cannot review/approve their own claim, reviewer cannot confirm even after getting APPROVER role, unreviewed confirmation denied, READER/OPERATOR cannot confirm. A distinct APPROVER confirms the fake external-reference fixture.
- Invoice/attempt become REFUNDED while full invoice JSONB except permitted status/version fields stays identical. Only that paid grant is revoked; a separate real Admin-created grant remains byte-identical and still confers actual entitlement. Repeated confirmation409 preserves all financial snapshots and one audit per state.
- A separate paid invoice DISPUTE is reviewed; attempting to confirm it as a refund409,then authorized rejection leaves financial tables except the case identical and invoicePAID. No automatic chargeback/refund algorithm or provider payout is claimed.
- Actual lifecycle refresh changes fixture ACTIVE→EXPIRED, preserves invoice/payment/grant/history snapshots, and a second refresh changes neither subscription JSONB nor audit count.

Final affected lifecycle-only `.local/billing-lifecycle-system-actor-green.log` TERMINALexit0:onePASS/zero failures/errors/skips,31.159seconds. It additionally verifies SYSTEM,explicit scheduler source and nonempty run correlation. The earlier11 and later1 are not an invented12-case Maven aggregate; unchanged finance cases were not needlessly replayed for this classification-only refinement.

The operator runbook is `docs/runbooks/seller-billing-finance-cases.md`:full-refund-only manual bookkeeping,three independent identities,dispute/reversal escalation,partial/unknown evidence stop,immutable ledger checks and explicit real-money boundary. Fixtures do not prove real refund evidence,tax/legal acceptance or the provider's actual chargeback handling.

## Remaining work and current gate

Canonical OpenAPI has zero reconciliation paths; source only exposes internal worker/service calls and an Admin alert count. No manual Admin reconciliation API/button/audit workflow exists. This is approved missing implementation,not an external/staging blocker. BIL021 stays NOT_TESTED with partial role/audit evidence; do not claim a permission test covers a nonexistent operation. Remaining original BIL008/020/021/023/025/028/029/030 and local recovery/security/operational/pilot requirements retain full scope.

Eighteen exact source copies/hashes `.local/billing-finance-audit-source-manifest.json` include current audit changes and finance fixtures. Gitleaks8.30.1 delta scan exit0/no findings,253658bytes, `.local/billing-finance-audit-security.log` and redacted JSON,no ignore/allowlist change. This is not whole dependency/image/CodeQL security acceptance; earlier unwaived limitations remain.

New sequential full verify is running in `.local/billing-finance-audit-full-verify.log`,same SSRbrowser/pilot/calendar-native opt-ins. It covers final exact source and the expanded private finance test configuration. No overlapping Maven,no predicted count,no full PASS before terminal output. Existing frontend production inputs/gates are unchanged; no unnecessary frontend replay or production image claim.

Next action: Finish this full gate,then implement the missing authorized/audited manual reconciliation operation and continue the remaining original local requirements.
Required from you: Nothing
Completion condition: Current full evidence is terminal and all remaining local implementation/acceptance gaps are closed with appropriate evidence,without crossing the real-money boundary.
