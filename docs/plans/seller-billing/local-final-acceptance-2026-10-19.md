# Seller Billing + payOS — final local acceptance

Status: **LOCAL COMPLETE** within the explicitly recorded scope. Implemented and tested locally;
**real payOS payment pilot NOT_READY** until the external prerequisites below are
verified. No staging, production, real charge, refund or deployment is claimed.

## Source and preservation

- Checkout: `C:\Users\tanbu\.codex\worktrees\seller-billing-local\CoVie`, detached
  HEAD `41abfce04276950db2b680d44cd1b6daad0106e3`. Fresh18:50ICT fetchexit0 confirms
  origin feature at the same SHA and main `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`.
  All work remains dirty/local; no new commit, branch, push, merge or deployment.
- Original dirty Social checkout and recovered data at `E:\WebApp\CoVie` are not
  used or built. Applied Billing/Social migration-number collisions are not merged,
  renamed or rewritten. Normal money/release/reconciliation/enforcement flags stay OFF.

## Changes within approved scope

The chronological reports and original BIL matrix retain each verified bug and its
focused evidence. Financial fixes cover verified quote/idempotency/recovery,
authorization, immutable audit/refund controls, clock/expiry/calendar/trial/renewal,
public/Need/ads boundaries, migrations/recovery, privacy/export, UI resilience and
observability. Existing working behavior is retained; no auto-debit or buyer fee.

The explicitly approved legacy decision remains: unverifiable legacy same-key retry
returns409 with authorized old invoice/status recovery, never a new invoice/link/grant;
fully bound current same-key/body replay remains successful. Both regressions execute
in the current full fixture. No new approval was requested for that decision.

Final original-plan gaps beyond30BIL were implemented narrowly:

- Admin price/policy publication preview with server values, date, honest all-status
  subscription-reference cohort and captured request/revision confirmation; stale409
  requires explicit reload/review. Public/current effective reads are unchanged;
  additive latest-published Admin reads include scheduled revisions.
- Read-only immutable publication history, including retired plans and actual stored
  publication/effective times; bounded pagination, typed client and stale-response guard.
- Finance-reader subscription lookup with optional exact merchant UUID, current plural
  ACTIVE OWNER membership UUIDs only, stored status/period/grace/cancel fields, scoped
  associated invoice pages and existing audit navigation. No new financial operation,
  PII enrichment, invented ownership or fabricated historical transitions.
- Confirmed read-table text fragmentation is fixed only on the new subscription table
  using nowrap inside its existing focusable horizontal scroller. No global UI redesign.

## Executed gates — separate actual runs

| Gate | Actual result | Evidence |
|---|---|---|
|Full backend Java25/PG/MinIO + SSR/pilot/Billing-browser opt-ins|1070reported,1069PASS,1HotQuerySKIP,0failures/errors;9m06s|`.local/billing-admin-read-final-full-verify.log`, independent279current suite XML in `.local/billing-admin-read-full-surefire/`, summaryJSON|
|Original skipped HotQuery opt-in|Separate actual1PASS0skip on an owned populated PG18/currentV034 dataset, unchanged source/schema; not retroactively a full-suite pass|`.local/billing-owned-perf-838d871c9fa5/hot-query-test.xml`, original performance provenance/report|
|Full Web final source|553PASS,12RedisSKIP;126filesPASS1fileSKIP;94.35s|`.local/billing-admin-read-web-final.log`|
|Redis opt-in|Separate actual12PASS46.67s on owned Redis8 with persistence/noeviction; unchanged auth source, not rerun for Admin reads|Prior actual Redis evidence in `local-security-observability-progress-2026-10-19.md`|
|Typecheck/full lint/Next production build|exit0; lint28existing warnings0errors; compile3.9s, TypeScript10.9s,38routes|`.local/billing-admin-read-final-{typecheck,lint,build}.log`|
|Official contract/client|export/generate/check exit0; current canonical exactly equals final full export;178paths235schemas|SHA256 `7897ED2807DDCD28C992AB49BE443231DBB97796ECF9F72330D528614328EA29` and generated-client evidence|
|Focused current Admin reads native|1PASS33.69s/Maven37.920s,360/390/1366;actual API/PG/builtNext/Chrome,3fake-paid fixture shops then browser0writes/0pageerrors;11tables fullJSONB unchanged|`.local/billing-admin-read-native-layout-final.log`, `.local/billing-browser-1792409496242/result.json`|
|Native read layout/audit|History/subscription native Tab+ArrowRight assertions; subscription field ranges single-line; no document overflow; associated invoice audit query200/one actual settlement event;3screens viewed|Same native fixture; geometry.left is sampled before animation, not a post-scroll measurement; actual await assertions prove positive scroll where required|
|Exact official full UI command|Owned `pnpm ui:verify-local -CurrentDesign -PostgresContainer <owned>` exit0; all journeys plus201automated captures/81cases; cleaned;21previously dirty non-Billing UI/launcher/support files retain0hash delta|`.local/billing-owned-ui-23e2c104bdcf/`, `.local/billing-final-local-retained-ui-source-check.json`; not relabelled new Billing-read proof|
|Current packaged pair|Fresh PG18/Redis8/generatedRSA/real login, new JAR+production Next;3Admin private reads HTTP200/no-store,6financial tables identical, browser0writes after login/0errors,360/390/1366 no overflow; owned cleanupTRUE|`.local/billing-owned-ui-00b4a04c6992/{result,cleanup,current-publication-labels,financial-read-snapshot}.json`|
|Current Java vulnerability scan|Trivy0.72 rootfs/networkNONE,186recognized Java packages,0HIGH/CRITICAL,exit0; cached vulnerability database scope|`.local/billing-admin-read-current-api-scan-result.json` and actual JSON inventory|
|Current Web production dependency advisory scan|pnpm audit --prod exit0,0info/low/moderate/high/critical reports|`.local/billing-admin-read-pnpm-audit.json`|
|Final working-source secret scan|2077Git-visible files inventoried; default Gitleaks scanned13.46MB text, rawexit1/7matches; every match reviewed as unchanged noncredential documentation,0unresolved; no ignore/config/detector change|`.local/billing-final-local-{source-manifest,source-scan-result,gitleaks-review}.json`; raw report/log retained, not misreported as exit0|

Packaged JAR SHA256:
`BFA190DC21A668B664699551D374D7C38F511598C86D113C830783815B64692E`.
No skip-tests build is used as completion evidence. No false combined aggregate is
constructed from independent focused, full and opt-in runs.

The seven default secret-detector matches are specifically: a Figma resource
identifier (`buyer-desktop-engineering-handoff.md:6`), three internal Prometheus
scrape-address references (`production-topology.md:7`, production-hardening plan:75/1572),
two documented idempotency-header examples (merchant/catalog plan:2181/2555), and
staging-checklist prose (original Billing plan:459). All five source documents are
unchanged from HEAD; exact file/line hashes and classifications are retained in the
review receipt. They are not authentication credentials. No broad allowlist was added,
no raw finding suppressed, and this review does not make remote CI green.

The current full BE and native read checks preserve the previously verified financial,
migration, backup/recovery and distinct-JVM crash/restart invariants on unchanged money
source. The old restart artifact remains identified as old; current packaged proof
here is boot/login/new-read integrity, not a newly repeated forced process kill.

## Requirement traceability and limitations

- Original30 BIL mapping: `acceptance-original-bil-v1-local-2026-10-17.md`.
- Whole original numbered requirements: `local-whole-numbered-plan-acceptance-2026-10-19.md`.
- Detailed final feature evidence: `local-publication-impact-preview-progress-2026-10-19.md`
  and `local-admin-read-completeness-progress-2026-10-19.md`.
- Figma comparison was waived by the owner:201automated captures are not201manual
  reviews; manifest records NOT_APPLICABLE_OWNER_WAIVER/NOT_PERFORMED and0manualPASS,
 201PENDING. Physical devices/screen-reader certification are not claimed.
- Metrics/rules/dashboard/query plans are locally exercised technical/synthetic
  evidence, not actual alert-channel delivery, field SLO/load/soak or a false-denial oracle.
- Source/Java/advisory scans are not an OS image, license, remote dirty-source CodeQL,
  full Git-history scan or guarantee that no vulnerability exists.
- A separately measured existing1280audit-filter overlap is outside Billing scope
  and remains unchanged. No claim of an entirely defect-free unrelated UI.
- Preview cohorts are explicitly advisory current subscription records, not exact
  future eligibility/revenue forecasts. Current membership IDs/invoices/audit records
  are not reconstructed missing historical ownership/state events.

## Only external payment/release prerequisites

1. Owner/operator provisions private API-only payOS credentials and confirms actual
   account/channel eligibility, minimum amount/fees and signed-response compatibility.
   No secrets in chat, client bundle or public evidence.
2. Verify public HTTPS app/webhook delivery, legal entity/VAT/e-invoice obligations,
   seller terms/privacy, on-call/support and actual alert destination/response.
3. Owner explicitly authorizes one pilot amount/merchant after those checks. Configure
   a dedicated supported Admin plan, leave enforcement/reconciliation OFF; enable the
   two real-money flags only under that separate authorization. No automatic renewal.
4. Owner performs one payment. Only then perform authorized read-only verification of
   actual provider signature/amount/order/invoice/merchant and single settlement/grant.
   Never treat a browser redirect or a fabricated callback as real payment evidence.
5. Follow `payos-setup-and-local-acceptance.md` and `real-money-pilot-gate.md`; preserve
   financial records and keep activation/deployment/cohort/legacy-cutoff approval separate.

No local business-rule approval question or credential is needed to finish the current
code/verification work. These external facts do prevent claiming a real payOS pilot or
production release is ready/completed.

Final handoff sanity: `.local/billing-final-local-sanity.json` confirms10 application/contract
source hashes unchanged, final JAR/Next provenance matched, no running containers or
listeners on3000/8081, no generated key files in the last owned runtime fixture, and
`git diff --check` exit0. The12-file final documentation-delta scan used default
Gitleaks rules with exit0/0findings (`.local/billing-final-document-delta-result.json`);
this does not replace or relabel the whole-source raw7/reviewed findings above.

Next action: Stop unchanged local goal work; begin a real pilot only on a separate authorized request with verified prerequisites.
Required from you: Nothing for local completion; private operator/provider/legal facts and separate money approval only for a later real pilot.
Completion condition: Local evidence and owned cleanup are recorded; real-money/release completion requires a separately authorized and reconciled actual transaction.
