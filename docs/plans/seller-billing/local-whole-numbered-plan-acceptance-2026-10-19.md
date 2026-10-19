# Original numbered-plan acceptance — local scope closed

Authority: original approved v1.0, not the remapped WIP BIL matrix. Local implementation
and verification are closed by `local-final-acceptance-2026-10-19.md`; real pilot
NOT_READY until the isolated external prerequisites pass. This is not production acceptance.
Execution checkout is the isolated dirty Billing worktree at41abfce; original dirty
Social checkout/recovered data are not used. Fresh18:17ICT fetchexit0 independently
confirms feature/HEAD41abfce and mainbb2e06 unchanged. No commits/remote writes.

The proposed physical table names, endpoint spellings and screen arrangement in the
original plan are not substituted for the implemented compatible API. Use generated
OpenAPI for actual paths and the original invariants for behavioral acceptance.

| Original section | Local implementation/evidence | Disposition and limits |
|---|---|---|
|0 scope/invariants|Billing separate from buyer orders; current timed-grant enforcement, membership/merchant isolation, server-only PSP adapter, no redirect settlement; original BIL001–030 traceability|PASS in the recorded local fake-provider scope. No commission, auto-debit, buyer fee or payout added|
|1 baseline/integration inventory|Remote heads above; original inventory plus public/query/ads/Need/order-boundary reports; Social data/source preserved|PASS local inventory; not a merge of colliding Billing/Social applied migration numbers|
|2 seeded/configurable policy|V020–034/SystemSettingRegistry, immutable plan/policy, calendar/trial/reminder/legacy tests, BIL013/014/020/024/028|PASS supported local rules. Defaults are not actual production activation; real legacy cutoff and tax facts remain external|
|3 domains/states/permissions|Current owner/member/finance access policies; stored state distinct from eligibility; original authorization/finance/calendar/settlement reports|PASS actual supported state transitions and source contracts, not an invented implementation of every suggested state string|
|4.1 config/history|Typed settings and price/policy versions; new Admin history reads/UI, retired-plan access and true publication/effective timestamps|PASS targeted/full/current native and packaged reads. No new mutable draft model|
|4.2 publication invariants|Existing row-lock/If-Match races, invoice snapshot preservation; new read-only previews/latest published Admin reads/captured confirmation; original BIL005/006/028|PASS local targeted/native scope. Preview cohort explicitly all-status subscription records, not exact future affected eligibility/revenue; policy does not rewrite snapshotted terms|
|4.3 calendar/renewal/trial|BillingPeriodCalculator, quote/settlement/trial marker; real UTC/ICT/leap-year/early+late renewal proofs in calendar-trial/onboarding reports|PASS local; real recurring debit outside P0|
|5 DB/Flyway/recovery|FreshV001–034, populatedV013/V019/V030 upgrades, repeated migrations, full JSONB dump/checksum/restore and process/transaction recovery reports|PASS recorded disposable data tests on unchanged migration/financial source. No destructive down migration/recovered-data exercise|
|6.1 provider choice/fees/channel|payOS adapter plus current runbooks and private API-only configuration contract|BLOCKED_EXTERNAL actual account/channel eligibility, fees/minimum, TLS delivery and owner/provider terms; fake transport does not establish these facts|
|6.2–6.3 payment/recovery|Signed fake provider HTTP/GET/webhook, atomic settle, replay/timeout/lost callback/rollback/concurrent checkout/reconciliation, scoped quote and old invoice status|PASS local fake-provider cases, including approved legacy unverifiable retry409/no new charge and current fully-bound same-key replay|
|6.4 refund/legal|Independent requester/reviewer/approver workflow, immutable cases/manual externally-executed-refund confirmation and runbook|PASS local technical case controls. BLOCKED_EXTERNAL actual bank refund, tax/VAT/e-invoice responsibility and legal terms; no automatic payout|
|7 contracts|Official export/generate/check; current read additions are additive; original quote/checkout/status/finance contracts retained|PASS; final full export exactly equals canonical178paths235schemas. Actual singular quote/path names are documented, not renamed to proposed examples|
|8 enforcement/commerce/Social|Original BIL001–004/015–020/024/026/027/030 and public/query-count/ads/order-boundary reports|PASS unchanged local paths; no PSP call authorizes ordinary sale, no deletion of social/history, historical orders remain fulfillable|
|9 Seller UI|Real onboarding/multi-shop/quote/QR/poll/recovery/renewal/calendar/fault-state native flows360/390/1366; shared Buyer/Seller acceptance and restored retry UI|PASS recorded local flows/semantics. Not physical device or screen-reader certification|
|10 Admin UI|Existing plan creation/retirement/config/ledger/finance roles/manual grants/cases/export/reconciliation; new preview/history/subscription/current OWNER membership/associated invoice reads|PASS final native single-line/keyboard scroll, associated invoice audit query and current packaged pair. Available records are labelled accurately; no reconstruction of missing historical state events|
|11 legacy/shadow/cohort/rollback|Read-only legacy/shadow preview, idempotent reviewed grants, local cohort and kill-switch preservation tests; BIL020/026|PASS local mechanisms and fixture rollout/rollback. BLOCKED_EXTERNAL actual old-merchant inventory/cutoff/notice/pilot and production false-denial monitoring; no unreviewed cutoff chosen|
|12 original30 BIL|`acceptance-original-bil-v1-local-2026-10-17.md` contains the original mapping, named tests and scoped outcomes; not `acceptance-matrix-v1.md` reused IDs|PASS recorded30/30 local dispositions with current full regression; no row implies real provider/staging release|
|13 G0–G6 gates|Local mechanisms/evidence in this matrix, original BIL matrix and final acceptance report|PASS local gates. BLOCKED_EXTERNAL staging/HTTPS/operator delivery/legal/production pilot; deployment and money flags stay OFF|
|14 integration/file map|Existing billing modules, supported merchant/catalog/checkout/Need/ads/settings/auth integration; generated client and matching actual routes|PASS implementation locations; suggested paths are discovery guidance, not a reason to rename working code|
|15 security/observability/operations|Authorization/CSRF/privacy/replay/audit failure tests; cached metrics/operation timers/config signals,17Prom rules/5synthetic fixtures/12dashboard expressions; actual HotQuery and3metrics SQL plans; current production pnpm audit0findings|PASS stated technical/synthetic scope; current JAR186Java packages0HIGH/CRITICAL and full source7reviewed documentation matches/0unresolved. Not a field SLO, real channel delivery, OS image/CodeQL/history scan or zero-vulnerability guarantee|
|16 ADRs|Approved default commercial scope and subsequent explicit owner legacy409 decision; ads expiry/legacy replay ADRs and independent financial controls|PASS implemented local decisions. Provider contracts, legal facts, actual migration cutoff and release/money approvals remain external facts, not supplied by broad coding approval|
|17 execution/acceptance|Focused RED→GREEN, preserved dirty source/data, separate actual runs, typed contract generation and no remote/deploy/money operation|PASS whole-local closure within stated scope; no real-money/release claim|
|18 references|Original plan remains unmodified authority; runbooks, original matrix and chronological reports retain source/artifact limitations|PASS traceability. Older ACTIVE/failed/historical snapshots are not silently turned into current passes|

## Current gate distinction

Exact owned `pnpm ui:verify-local -CurrentDesign -PostgresContainer <owned>` passed
at fixture23e2c104bdcf and cleaned. It validates201automated captures/81cases, while
manual Figma comparison is owner-waived/unperformed,0manualPASS/201PENDING. The command
predates the new Billing read components; its unchanged route evidence is retained,
not falsely relabelled current Billing-read native proof. Current Billing reads have
their own actual API/PG/native evidence in `local-admin-read-completeness-progress-2026-10-19.md`.

Final full BE94985 exit0:1070reported/1069PASS/1HotQuerySKIP/0failures/errors9m06s,
279independent suite XML. Final Web553PASS12RedisSKIP94.35s includes the minimal
subscription-table nowrap/audit-navigation follow-up; focused native1PASS33.69s and
current packaged-pair reads also pass. Prior unchanged Redis opt-in12PASS and original
HotQuery1PASS remain separate runs, not fabricated aggregates. All owned resources
cleaned; current artifact/scanner receipts are linked in the final acceptance report.

Unrelated existing audit-filter input overlap at1280 was measured and a prototype
confirmed; no production fix was made outside Billing scope. Automated full UI success
does not mean every possible visual defect is absent.

Next action: Preserve final local evidence and wait for a separately authorized real pilot; do not repeat unchanged goal work.
Required from you: Nothing
Completion condition: Local Billing acceptance is recorded; real pilot/release facts remain explicitly isolated and require actual authorized evidence.
