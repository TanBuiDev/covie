# Billing security and observability progress — 2026-10-19

Whole original Seller Billing goal remains ACTIVE / NOT_READY. Original BIL001–030
local acceptance is not proof that every wider gate is complete. No remote write,
commit, real provider payment, feature activation, deployment, recovered-data mutation
or original E checkout `.next` build is included.

## Packaged dependency risk repaired

Primary vendor advisories and Maven availability verified before minimal POM changes:
Jackson2 BOM2.21.7, Jackson3 BOM3.1.7, Tomcat11.0.26, and removal of the old explicit
Netty override so the existing Boot4.1.1 BOM provides4.2.17. Tomcat11.0.25 was not used:
the vendor security page still identifies issues fixed in11.0.26. No broad parent,
AWS, Springdoc, PostgreSQL or frontend dependency upgrade.

- `.local/billing-security-patch-targeted-package.log`: Maven57719 terminal exit0,
  8PASS/0skip, 1m46s; password authentication1, existing-subscriber effective renewal1,
  PayosGatewayHttp4, OpenApiExport2. Actual package/repackage, no skipTests claim.
- Canonical OpenAPI/client before/after hashes identical; official generation/check
  succeeded (`billing-security-contract-{before,after}.json`, client logs).
- Patched JAR SHA256 `01DA5864E537CEF17138190A2B6A9AB7762EC92F53D747840F1B0966D9381278`.
  Correct Trivy0.72 rootfs scan with cached databases, networkNONE:186 recognized Java
  packages, zero HIGH/CRITICAL, scanner exit0. Evidence:
  `.local/billing-patched-api-scan-result.json`,
  `.local/billing-trivy/patched-api-rootfs-vulnerabilities.json`.
- Inventory186→186,25 version changes only in the four intended dependency families;
  unexpected changes0 (`.local/billing-security-patch-inventory.json`).
- Historical initial fs scan recognized zero language files and is explicitly NOT a
  passing Java security gate. Correct old rootfs scan found14 HIGH/CRITICAL findings.
  This is not an exploit demonstration, complete image/OS/all-severity/license scan,
  CodeQL result, or immutable remote CI proof.

The scanned package predates the new metric classes/hooks. It is now a historical
dependency-patch artifact: stable final source still needs package/JVM verification
and a scan bound to its actual hash.

## Operational gap implemented with targeted evidence

Original section15 lacked these signals despite generic HTTP/outbox/shadow metrics.
No financial transaction/pricing/authentication/API/schema change was needed.

- Cached three-query DB snapshot: retained invoices/subscriptions by finite stored
  statuses, time-valid unrevoked granted merchants, pending reconciliation/check age,
  snapshot freshness/failure counter, and worker/gateway configuration capabilities.
  Initial/failed business snapshots use NaN, not false healthy zero. No scrape SQL.
- Finite operation/outcome timers: checkout, committed webhook settled/replay/rejection
  and observed entitlement duration. Best-effort metric failures preserve action
  return/error and exactly one invocation. Timers count requests, not invoices.
- Actual HTTP scrape missing names RED87315→GREEN; signed operation missing timers
  RED28169→GREEN. Chunks1/2 final4PASS54327; config-gauge phase3 final65253 terminal
  5PASS/0skip,72s (HTTP2, unit3). Log `.local/billing-metrics-phase3-focused.log`;
  independent XML `.local/billing-metrics-phase3-xml/`.
- Normal worker configuration is OFF in HTTP fixture, but its explicitly substituted
  loopback gateway advertises creation capability1. This is NOT a normal real-money
  gatewayON assertion. Actual all-normalOFF packaged scrape remains to be executed.

Reviewer identified an additional real monitoring gap: repeated failed provider
checks update last_checked_at, so age alone stays low. New actual tampered signed GET
records PROVIDER_FAILURE with fresh age<5s, then asserts the missing review gauge:
RED94510 terminal1FAIL, not a financial failure. Minimal same-query aggregate now
counts pending invoices with latest non-NONE discrepancy; verified clean retry or
terminal invoice removes them. No extra query, identifier label or settlement change.
GREEN focused run7021 was running at this checkpoint; inspect its same handle/log
`.local/billing-reconciliation-review-signal-green.log` before claiming a result.

## Rules, dashboard and boundaries

Four original new alerts passed actual missing-rule RED→GREEN. Configured cached
Prometheus3.6.0 networkNONE checked16rules and all five rule fixtures successfully.
Extra stale/cross-instance fixture also passed. Fifth review-required alert separately
passed missing-alert RED→GREEN with fresh-check/firing/recovery/OFF semantics. Final
17-rule/all-fixture aggregate check remains due after this latest rule delta.

New provisioned `covie-billing.json` has10 panels/12 expressions. Actual configured
promtool evaluates all expressions against explicit synthetic and empty inputs:
`.local/billing-dashboard-expressions-final.log` exit0. Counts are per instance,
not misleading sums of replicated DB snapshots; duration is mean, not p95.
No Grafana rendering, real Alertmanager destination or delivery is claimed.

Runbook `docs/runbooks/seller-billing-observability.md` maps signals/actions and
technical pilot thresholds, preserves worker/creation OFF alert guards, and does not
treat unpaid invoices as lost transfers or metrics as authority to grant/refund.
There is no invented entitlement cache or erroneous-denial oracle. Table-scan cost
remains data-dependent despite bounded query count/result cardinality; representative
plans are required before capacity claims.

## Remaining whole-scope work

1. Collect current focused result, read-only review latest discrepancy signal, final
   rule checks. Stable-source backend full verify with cached MinIO and SSR/pilot/native
   opt-ins, current contract check, package/current new-JVM/OFF scrape/security scan.
2. Finish whole numbered original-plan traceability, not merely original30 BIL rows.
   Investigate exact `pnpm ui:verify-local` on an explicitly owned disposable stack;
   do not seed its default existing/recovered DB. Disposition the actual HotQuery opt-in
   against owned pilot-scale fixtures without relabeling skipped tests as passes.
3. Refresh scoped secret validation and stale pilot/runbook status. Unchanged frontend
   full539PASS12skip/native evidence is retained, not rerun solely for backend changes.
4. Actual provider account/channel minimum, HTTPS, legal/VAT, real notification channel
   and explicit human payment/activation remain distinct operator/external gates.

Next action: Collect same focused7021 handle and finalize stable Billing operational evidence.
Required from you: Nothing
Completion condition: New review signal passes actual provider-failure/retry regression and final rules, without financial behavior changes.

## Subsequent verified results — 02:25 ICT

- Run7021 is TERMINAL:3PASS/0skip/0failures/errors,66s. The new real PG/HTTP/owned
  signed-provider regression passed25.23s; two snapshot/configuration units passed.
  Independent XML saved in `.local/billing-review-signal-green-xml/`. Fresh failed
  checks stay visible; verified retry clears one discrepancy; full financial snapshot
  remains unchanged and exactly one original provider create was retained.
- Read-only reviewer approved the final discrepancy signal/alert semantics with no
  concrete remaining defects. Reviewer did not edit or run tests.
- Final configured promtool check found17rules and all five rule fixture files passed:
  `.local/billing-final-17-rules-check.log`, `.local/billing-final-17-rules-unit.log`.
  No actual alert destination, provider or Grafana rendering was exercised.
- Test-only packaged restart fixture now checks actual normalOFF metrics on initial,
  pending and paid snapshots, plus committed settlement timer and no identifier labels.
  This new fixture delta is NOT runtime-verified yet and must run against the current
  rebuilt JAR with the existing artifact-byte preflight intact.
- Stable production-source full backend verify98183 is RUNNING:
  `.local/billing-security-metrics-full-verify.log`, cached MinIO override and all
  SSR/pilot/native opt-ins explicitly supplied. Poll the same handle; do not restart,
  overlap Maven, edit compiled Java during the run or predict final counts. Save
  independent suite XML before the next Maven command.
- Scoped Gitleaks8.30.1 copied22sourcefiles318331bytes with exact SHA matches,
  exit0/no findings (`.local/billing-operational-source-manifest.json`,
  `.local/billing-operational-gitleaks.log`). Subsequent changes are report/AGENTS
  checkpoint text only; this is not whole-source/image/history/CodeQL validation.
  `git diff --check` passed.
- Stale setup/pilot documents now reflect source-backed recovery, RBAC, reminder and
  entitlement implementation without pretending real account/production acceptance.
  Compose requires separate API/Web files; deploy/rollback's legacy validation
  variable must point to the same private API file, with both Compose variables set.
- Exact G5 UI orchestration had no way to forward an owned PostgreSQL target despite
  the seed script supporting one. New mocked script regression RED unknown parameter
  → minimal optional `-PostgresContainer` forwarded to both seed invocations → GREEN;
  defaults, serial spec order and single-worker behavior retained. Logs:
  `.local/billing-ui-owned-orchestration-{red,green}.log`. This ran NO Docker, browser
  or database commands; exact UI command acceptance remains NOT_TESTED, not PASS.

Next action: Collect full backend98183 result and snapshot independent XML before current-package verification.
Required from you: Nothing
Completion condition: Current backend gate is terminal with exact pass/fail/skip evidence; current-JAR recovery/security and remaining whole-scope local work continue afterwards.

## Current artifact accepted — 02:34 ICT

Full98183 is TERMINAL exit0:1065reported,1064PASS,1HotQuery opt-in SKIP,
0failures/errors9m46s02:31:17. Independently copied only the279 suite XML files
actually listed as running in this invocation; stale explicit packagedIT XML was
excluded. Aggregate sums match Maven exactly in
`.local/billing-security-metrics-full-summary.json`. Initial extraction helper had
a JavaScript backslash-escaped regex yielding no classes; corrected to literal
prefix matching without changing/rerunning any test.

Official client generate/check both exit0; canonical SHA65CF69DD1A0DDAD56E443668B3AA59859874A48C37F2C917AA97F62586F2B6E3
and generated schema SHA EFC32042B7E349A0CCF86BD442AF165C2E2763BD20A6BE662EDCB9824B01165C
are byte-identical to before dependency/metrics changes.

Current full-built JAR SHA256:
`FE8F6BB86E6F81C5AB6D31C51505711E34D401766F23D05B1E172B62ED3BA324`.
Actual cached Trivy0.72 networkNONE rootfs exit0,186 recognized Java packages,
zero HIGH/CRITICAL. Exact hash/scope in
`.local/billing-operational-current-api-scan-result.json`; this is not an OS/image,
all-severity/license/SAST or immutable remote CI result.

Explicit packagedIT42214 TERMINAL1PASS35.90s/Maven62s02:33:19:
`.local/billing-operational-packaged-restart.log`, independent
`.local/billing-operational-packaged-restart-final.xml`, fixture
`.local/billing-jvm-restart-7586560504125035787/result.json`.
Actual firstPID8312→forced death at observed outbox INSERT lock→newPID14712,
same live freshPG18/Redis8 and issued HttpOnly session, exact seven-table rollback,
signed retry settles once, duplicate leaves paid snapshot identical, zero provider
requests. Generated keys removed. Current compiled classes/resources/JAR byte preflight
passed. Ledger pending row is explicitly seeded, not another checkout-intake proof.

Actual current packaged HTTP scrape files show initial pending0/paid0 → seededpending1
→ callbackpaid1/pending0, worker0/creation0 throughout; settled and duplicate timers1,
no merchant/invoice IDs in labels. These local exposures bind loopback, with test-only
1-second refresh; no new public financial endpoint or production delivery claim.

Pinned Playwright CLI install74640 terminalexit0 installed Chromium151.0.7922.34 and
headless shell1234 plus required helper binaries, no app dependency upgrade.
Exact owned UI orchestration60045 is now RUNNING under `.local/run-billing-owned-ui.ps1`,
fixture `.local/billing-owned-ui-71a03249924d/`: fresh named tmpfsPG18/user+DBcovie,
Redis8, generatedRSA3072, current APIJAR/localOFF and existing production Next build
atlocalhost3000. First seed succeeded and first real Playwright test started. No
recovered/default DB or original E `.next` involved. Private log filenames reflect
a runner-only case-insensitive `$Name`/`$name` loop collision (`DB_PASSWORD.log` is
the API stdout filename, not password content; `API_INTERNAL_BASE_URL.log` is Next).
Correct the ignored helper's log-parameter name before any future invocation; do not
restart this live fixture just to rename logs. Final cleanup/result still must be read.

Next action: Collect same UI60045 handle, inspect actual failures/screenshots and verify owned cleanup.
Required from you: Nothing
Completion condition: Exact UI command has scoped results and all owned resources/keys are removed, with no recovered-data mutations.

## Exact UI command investigation — 02:42 ICT

First owned run60045 terminated exit1: direct production Next had no development API
rewrites; trace showed Google-status/CSRF404 before any login POST. API readiness was
healthy. This was fixture ingress, not a Billing/authentication product failure.
Owned keys/processes/containers were removed; private failed trace retained under
fixture71a03249924d. The ignored helper now fixes its log-parameter collision and uses
an owned loopback Node HTTP ingress matching current production Nginx routing:
`/api/v1/` to API except `/api/v1/auth/ssr-refresh` to Next, all other paths to Next.
No app route/security/contract changes or browser request mocking.

Second owned run68319 terminated exit1 after2.1minutes at an obsolete test step:
Google-status/CSRF/login and address API all200, successful real login/home, but the
test tried to select a saved-address radio while the upgraded location chooser was
still on its initial choices page. Actual screenshot viewed and current component
source confirms the missing navigation step. Minimal test-only correction adds the
existing `Địa chỉ đã lưu` button click before the unchanged address selection/asserts.
No location app behavior change, fake success or weakened assertion. Private failure
trace retained under fixture37e517013207; cleanup confirms keys removed/processes
stopped and no labeled container remains.

Third exact owned run1863 is RUNNING, fixture1462136900ea,
`.local/billing-owned-ui-current-step-run.log`. Do not claim UI PASS yet. Both seed
invocations legitimately run dependency:copy-dependencies for their existing Argon2
fixture hash tool; do not overlap another Maven command. No original E checkout
build, recovered/default DB or real payment. Current source/JAR/backend gate stays
unchanged; only test/helper/doc changes follow full verification.

Fresh `git fetch --prune origin`02:37ICT exit0:
feature+local HEAD41abfce04276950db2b680d44cd1b6daad0106e3,
mainbb2e06b41282c8e9205c35a13e7b6adec68ceaa2. No remote write or merge.

Next action: Collect same routed/current-step UI1863 handle and inspect its next actual result.
Required from you: Nothing
Completion condition: Exact local UI command is verified against current interactions, without altering working product behavior or recovered data.

## Current Buyer flow verified — 02:54 ICT

Third owned run1863 terminated at a loose heading selector during client navigation:
the old home page had two partial `Khám phá` headings before the Search H1 appeared.
The test now asserts the actual `/search` URL and exact H1; no app alteration.

Fourth run33520 exposed a runner-only environment defect on product SSR: .NET
`SetEnvironmentVariable(name,null)` in this PowerShell runtime creates an empty
child variable, confirmed by a controlled Node probe (`hasOwn=true,value=empty`).
The Web schema correctly rejects empty REDIS_URL. Remove-Item Env produces genuine
absence (`hasOwn=false`). Fixed only the ignored runner's clearing/restoration,
not Web validation. Fourth owned stack cleanup verified before the fifth startup.

Fifth owned helper15365, fixture0713335c0c97, is held alive for targeted diagnostics
with a bounded automatic cleanup deadline03:07:27ICT. An explicit
`diagnostics.done` file triggers earlier cleanup. It owns only its PG18tmpfs/Redis8,
generatedRSA3072, API29444/helper11796 and Next/ingress onlocalhost3000.

The next original test failure came from relying on implicit coordinates while
the browse context held a saved-address ID. The current form requires a concrete
point. Test now explicitly selects the supported SAVED source and the actual fixture
address through normal controls before creating an address and before its existing
422-field-error exercise. No coordinates invented, address product logic changed or
saved-context CURRENT-mode behavior declared fixed. That default-mode limitation
is separately observed, not evidence of a Billing regression or a reason to rewrite
the unrelated address subsystem.

Focused20771, on the partially mutated diagnostic fixture, reached real address
create/edit/error and cart, then failed at obsolete `Xem trước`: current checkout
auto-previews and already showed enabled `Đặt hàng`. Existing checkout component and
unit tests confirm this current behavior. Updated E2E to await the actual server
POST preview200 and nonempty checkoutToken, exact checkout URL and enabled commit
instead of clicking a nonexistent button. No monetary logic or assertion weakening.

After a CLEAN official owned reseed, focused99269 TERMINAL1PASS9.8s/test10.8s,
`.local/billing-owned-ui-0713335c0c97/populated-current-checkout-focused.log`.
The whole populated Buyer journey now reaches server preview and first real buyer
order commit/success/history with pageErrors0. Product360 and success390 screenshots
actually viewed. This is one real E2E result, NOT all UI flows or Figma acceptance.

Exact full `pnpm ui:verify-local -PostgresContainer billing-owned-ui-0713335c0c97-pg`
is currently RUNNING57146, log `ui-current-full.log` inside that fixture. It reseeds
for required isolation and then runs the other Buyer/Public/Seller/Admin/manifest
flows. Do not predict results or overlap Maven while the seed hash tool resolves
dependencies. The held helper's initial failed verdict remains unchanged; the manual
full command has its own result. Clean up only after the active test command stops.

Scoped Gitleaks24copiedfiles370374bytes exit0/no findings at02:44; that snapshot
predates the latest address/preview test-only lines and this report appendix.
Typecheck exit0 at02:46 predates those last steps too. Refresh affected final source
checks after the UI test source stabilizes; unchanged app Vitest539PASS12skip/build
and full backend/current-JAR results are retained without irrelevant replay.

Next action: Collect same exact full UI57146 and address only its next actual failure.
Required from you: Nothing
Completion condition: Remaining current-design UI flows have actual results, then owned resources and keys are removed; whole-plan acceptance continues beyond this Buyer pass.

## Exact UI remainder and measured header overflow — 02:59 ICT

Full UI57146 is TERMINAL exit1. The populated Buyer flow passed again9.6s/10.6s
inside the exact required command, then an official reseed isolated its writes.
The subsequent19-case Buyer visual flow failed6.8s on an obsolete GPS helper string.
Current address form no longer renders that text. Test-only assertion now checks the
actual delivery-address group and GPS control, preserving the hidden coordinate
assertions. No application or business logic changed.

That focused Buyer run is TERMINAL FAIL2.1s for a real layout defect:
`scrollWidth1205 > clientWidth1024`. The existing overflow guard remains intact;
do not hide the header, weaken the guard or blanket-approve historical Figma reviews.

Independent current Chromium151 authenticated DOM diagnostics reproduced overflow:

| Viewport | Document scrollWidth |
|---|---|
|390|390|
|360|360|
|1024|1295|
|1280|1295|

Exact structural evidence:
`.local/billing-owned-ui-0713335c0c97/address-overflow-diagnostics.json` and log,
generated by `.local/diagnose-owned-address-overflow.cjs`. No password, cookie or
request body is printed by this diagnostic. The overflowing nodes are the common
`buyer-desktop-header__right`/nav/actions, not the address map. Right group713px
extends fromleft582 toright1295; existing left/right flex-shrink0 and the later
desktop width rules need a narrowly scoped responsive layout correction preserving
all navigation/actions/accessibility. Source files:
`apps/web/app/buyer-desktop.css`, `apps/web/components/layout/buyer-desktop-header.tsx`.
No CSS/header production fix has been made yet. Prototype/attribute the minimal fix,
then verify actual rebuilt production behavior before claiming the defect closed.

All test processes are now terminal. Explicit diagnostic marker triggered helper
15365 cleanup at02:58; its initial failed result remains unchanged. Cleanup confirms
owned processes stopped/keys removed, labeled containers0, normal3000/8081 listeners
absent. No default/recovered database or original E `.next` was touched. Current API
JAR hash rechecked unchanged. Future Web artifact build is now safe; if CSS changes,
the old Web build/full539 tests become historical until proportionate current gates,
while unchanged backend1064/current-JAR proofs must not be replayed without cause.

Next action: Implement and verify only the proven responsive header layout defect while preserving every control and API behavior.
Required from you: Nothing
Completion condition: Actual production1024/1280 widths no longer overflow, then exact UI remainder and whole-plan local acceptance continue without fabricated visual/provider evidence.

##03:10ICT — proven responsive defect fixed, whole goal continues

Focused current-production regression failed at1024:scroll1295. Browser-only wrap
prototype fits all six widths, but actually reproduced Need-create sticky overlap:
header115/sidebar88 at1024/1280. A short Need-detail column also scrolls out of its
containing block at500px; that is not evidence its sticky behavior was newly broken.
Minimal production change preserves all labels/controls/DOM order: desktop flex
wrap/auto height and existing single-row minima; measured header-height variable
using existing optional ResizeObserver/resize pattern; only two existing Need sticky
offsets use measured height+16. No financial/API/schema/dependency change.

New observer unit actual RED, then3PASS4.92s after adopting existing optional-observer
pattern for jsdom compatibility. Native current production rebuilt exit0, focused
fixture `billing-owned-ui-d8474de9cf46`1PASS8.9s/11.6s at360/390/1024/1280/1366/1440:
document fits, every original desktop control visible/named/bounded/nonoverlapping,
Tab order unchanged, both Need computed sticky offsets clear header and create
preview clears header after actual scroll.1024/1280/1440 screenshots viewed. No CSS
injection in acceptance. Typecheck/scoped lint0. Fixture cleanup keysRemoved and
processesStopped TRUE, normal money flags OFF. Failed prototype fixture also cleaned.

Current full Web67505 is RUNNING, not PASS. Exact official owned UI18190 is RUNNING,
fixture `billing-owned-ui-1ebf30bbd8b0`, not PASS. Do not reuse old539PASS as current
full evidence after shared header source changed. Unchanged backend1064PASS/current
JAR FE8F6B... evidence remains valid; do not replay full BE for this layout change.

Next action: Finish full Web and exact Buyer/Public/Seller/Admin UI; resolve only actual failures.
Required from you: Nothing
Completion condition: Current source gates terminal and whole original-plan gaps accurately dispositioned, not merely header or30-row completion.

##13:15ICT — full Web, real opt-ins, current Buyer/Public progress

Previous goal turn made actual progress, not a blocker. Full Web67505 terminal:
124files PASS/1file SKIP;540tests PASS/12 Redis opt-in SKIP,95.85s. Separate owned
Redis8 AOF/always/noeviction opt-in49618 terminal12PASS46.67s, explicitly not another
full suite or a production durability claim; owned Redis removed. Full ESLint11364
exit0 with28 existing warnings; scoped lint/typecheck/build were already0. No
unchanged backend replay. Current scoped31-file/487333-byte Gitleaks default-config
scan0/no findings at03:10 predates later E2E/helper changes and must be refreshed.
First scanner invocation referenced nonexistent `.gitleaks.toml`, so did not scan;
the actual successful default-config run is the evidence, not that failed invocation.

Exact owned UI18190 terminal failure preserved: populated Buyer1PASS11.7s; Buyer
visual failed because error exercise lacked a valid point. Corrected test only to
select a real SAVED fixture point and await actual intercepted POST422. Subsequent
zero-reputation assertion incorrectly matched percentages outside the actual metric;
scoped to the real summary metric, keeping no-percentage invariant there. Subsequent
503 exercise never received503: trace only200 and production service worker forwards
GETs, bypassing page-route fault injection. Scoped Buyer test now blocks service
worker for deliberate injected-error tests and requires actual503 before retry UI.
No Address/Reputation/PWA business source changed. New clean owned Buyer-only
fixture971316e9be27 actual entire Buyer1PASS21.9s/23.2s and cleanup confirmed.

During continuation OS clock advanced from03:11 to13:07; old18190 handle and helper
were authoritatively absent, all old owned containers/port3000 absent, but PEM files
remained. Removed only the two named owned PEM files; recovery-cleanup.json records
actual cleanup, not a claimed successful finally block. A compound cleanup command
was rejected before execution; direct literal-file cleanup succeeded. Old failed
result remains unchanged.

New exact UI86454 fixture03f528ed48d2: populated Buyer1PASS13.4s and full Buyer
visual1PASS17.0s, then Public failed only the expected guest GET401s. Trace location
identifies exact `/api/v1/me/carts` and `/api/v1/auth/ssr-refresh?client=1`. New helper
regression actual RED→10affectedPASS1.12s proves only explicit exact guest401 URLs
are accepted, while foreign401/sameURL500/page errors remain failures; default
collector remains strict. Public focused1PASS3.9s/5.2s. No authorization or public
application change. Current runtime permits guest denials; do not claim all network
requests200. Seller focused then actual strict-selector failure: current banner and
content both have H1 `Thêm điểm bán`. Test scopes to visible route-specific Seller
banner plus exact destination URL; no `.first()` workaround or application change.
Seller18824 is currently RUNNING; Admin/manifest still pending. Current source
typecheck0 after diagnostics changes. Do not claim exact full command PASS.

Previously skipped named HotQuery gate now separately actually ran: fresh owned
PG18/PostGIS, current unchanged migrations001–034, existing pilot fixture minimums
checked by the original test, exact captured repository notification/outbox SQL
with recorded literal fixture bindings. `HotQueryPlanIntegrationTest`1PASS0skip
0.593s/Maven33.126s, independent XML and provenance retained under
`.local/billing-owned-perf-838d871c9fa5/`. Original full1064PASS+1SKIP remains that
historical aggregate; do not rewrite its XML or invent a combined full1065 run.

Same separate owned database also has explicitly synthetic1000 subscriptions,
50000 invoices/attempts,5000 observations and45000 grants, including retained
historical rows. Exact current3metrics queries were extracted from production
source, not hand-reimplemented. Actual EXPLAIN ANALYZE result rows2/1/1,
execution10.609/0.192/17.961ms, shared hits1667/17/2703, temp writes0. Actual pending
5000/review500/entitled1000 asserted. Aggregates do scan retained rows; this is one
local synthetic planner observation, not a load/soak/capacity/SLO or provider proof.
No speculative indexes or query change. Initial ignored harness failed on Windows
Asia/Saigon alias and then unquoted PowerShell JVM property; fixed harness UTC
quoting only. All three owned perf databases removed. Current API JAR unchanged
FE8F6BB86E6F81C5AB6D31C51505711E34D401766F23D05B1E172B62ED3BA324.

Next action: Finish current Seller/Admin/manifest evidence and final whole-plan local audit, correcting only reproduced defects.
Required from you: Nothing
Completion condition: Current full-scope local gates and original requirements are evidenced; external real-provider/operator/money gates remain explicit and OFF.

##13:24ICT — narrow Seller order layout repair and remaining original scope

Actual Seller main journey progressed after scoping duplicate H1 to its exact mobile
banner, then failed at360 with document608px. Independent authenticated DOM geometry
confirmed the order page's implicit grid column expands to592.203px from intrinsic
status-tab width, stretching KPI/filter/list children; desktop document fits.
Browser-only `minmax(0,1fr)` prototype restores360/390/1024/1366 document widths.
The new focused test initially failed on a desktop-hidden mobile banner (harness,
not product RED); after setting the intended initial360 viewport, confirmed actual
RED608>360 in1.2s. Existing original Seller gate independently reproduced the same
defect. No overflow clipping or dropped controls is accepted.

Minimal production change: add `seller-order-page` to the existing Seller-only order
section and set its one grid column to `minmax(0,1fr)`. Buyer order branch, API,
sorting/filter/state transitions and all other Seller stacks are unchanged. Actual
rebuilt Next production build21154 exit0/38routes/compile2s/TS5.2s. Fresh owned
fixturef37453a3c688 native1PASS3.4s/4.5s at360/390/1024/1366: document fits, all4 KPI
controls bounded, search retained, native Tab reaches the last horizontally scrolled
status tab inside the viewport.360/1366 screenshots viewed.19affected unit cases
PASS8.50s; typecheck/scoped lint0, one pre-existing unused-import warning. The earlier
Web540 full is historical for this new Seller source; stable final full remains due.

Seller acceptance then exposed stale native-select/fieldset/file-input assumptions
after the existing custom-dropdown/studio UI upgrade. Test-only changes use real
combobox/option clicks, validate hidden PRODUCT/location values, current named
variant region and actual file input; no synthetic DOM events or production form
changes. Last actual failure41.8s was only old selected-image status copy; updated to
the current exact1-image status. Seller/Admin fault-injection specs now use the same
demonstrated service-worker-block setting as Buyer so deliberate GET errors really
reach the page; no claim of testing PWA behavior in those error exercises.
Seller83251 is RUNNING, not PASS. Owned helper76351 holds successful focused fixture
for follow-up until13:39:03ICT or its exact diagnostics.done marker; cleanup not yet
performed. Do not rebuild while owned Next is alive. Previous03f fixture explicitly
cleaned after tests terminal; its initial full-command failure remains truthful.

Fresh13:18 git fetch0 again verifies feature/HEAD41abfce/mainbb2e06 unchanged. gh CLI
is unauthenticated, but the connected GitHub tool independently confirms PR31 is
open/Draft/unmerged and head41abfce at13:19. The old CI SHA in its body is not current
dirty-source CI. No remote write/merge/commit/branch/deploy.

Whole-plan inspection found a genuine remaining requirement beyond the30rows:
approved4.2.5 Admin publication impact preview. Current catalog service and Admin UI
have immutable publish/If-Match/generic confirmation but no price/policy impact
preview; the only preview in Billing is unrelated legacy migration. Recorded in
`local-whole-plan-gap-audit-2026-10-19.md`. Must implement the additive read-only
preview within approved scope after current UI evidence, not call whole project
complete or reinterpret generic confirm as fulfilling it. No approval question is
required; no new pricing rule/money behavior is authorized by that implementation.

##17:52ICT continuation — completed UI fragments and real preview implementation

The13:24 live-process paragraph above is historical. All held fixtures subsequently
cleaned themselves: keys removed/processes stopped, labelled PG/Redis removed.
Actual Seller final1PASS47.3s/48.4s covers36cases/111captures after the confirmed
mobile catalog min-content overflow fix (one existing grid declaration only).
Admin audit actual1024document1132 overflow was independently attributed to its
header outside the inner930px scroller; common labelled keyboard-focusable scroller
plus the existing930floor corrected it. Current Admin1PASS22.2s/23.4s includes native
ArrowRight/internal scroll/documentX0;1024/1280 screenshots viewed. Production
build/typecheck/scoped lint exit0. An apparent audit-filter input overlap in one
screenshot remains an unconfirmed geometry hypothesis, not a claimed fix.

The official optional `-CurrentDesign` now retains all automated PNG/hash/identity/
route/state/overflow/error checks while explicitly recording the owner-waived Figma
comparison NOT_APPLICABLE and manual reviews unperformed (0PASS/201PENDING).
Default strict mode is unchanged; launcher regressions pass in actual pwsh and
Windows PowerShell5.1. Filesystem-only current manifest1PASS274ms/855ms covers all201
automated captures/81cases:Admin48,Public12,Buyer30,Seller111. This is not yet the
single exact final owned whole-wrapper execution, and not201 manual/Figma reviews.

Original4.2.5 preview gap is now implemented after a read-only reviewed narrow plan,
actual HTTP404/405 RED, immutable snapshot proof, corrected existing anonymous CSRF
expectation and focused green. See `local-publication-impact-preview-progress-2026-10-19.md`.
New latest-published Admin GET reads prevent scheduled-revision staleness without
changing public/effective-now contracts. Two preview POSTs are Admin/no-store/read-only;
confirmed body/revision stay captured across async field edits and409 requires explicit
reload/review. Population is honestly all-status subscription records, not an exact
affected-merchant forecast. Canonical176paths/233schemas diff against actual previous
65CF69DD... is precisely four new operations/two schemas, zero old contract changes.
Client generate/check, final typecheck/scoped lint/production build exit0; nine UI
tests PASS before test-only unchecked-index typing correction. Native pricing/package
93945 and final fullWeb99843 are RUNNING, no success claim yet. BE full1064PASS+1skip,
oldJAR/Trivy proof now historical for the new read-only API methods; stable final
full/artifact evidence is still required. No money/remote/release/flags/data changes.

## Final local closure

All RUNNING/local ACTIVE statements above are historical. Final authority is
`local-final-acceptance-2026-10-19.md` and the whole-numbered-plan matrix: actual full
BE1069PASS+1HotQuerySKIP9m06s/279suite XML, final Web553PASS12RedisSKIP94.35s, separate
unchanged opt-in proofs, new preview/history/subscription reads and real native/API/PG
acceptance, current JAR BFA190DC.../Next lAC_u_y7plWCBPzSYws9H packaged-pair boot/login/
private-read integrity. All owned resources/keys cleaned. No new money/source-schema/
dependency/remote/commit/activation/recovered-data operation.

Current cached Trivy JAR inventory186Java packages0HIGH/CRITICAL/networkNONE, current
production pnpm audit0. Full Git-visible source2077files has default Gitleaks rawexit1/
seven matches, all explicitly reviewed unchanged noncredential documentation;0unresolved.
Exact line/file hashes/classifications and raw report are preserved; no ignore/config/
detector was weakened and no remote CI success is claimed. Source/Java scans are not
OS image/license/full history/CodeQL or real alert-channel acceptance.

The exact CurrentDesign201automated command genuinely passed on its bound fixture;
21previously dirty non-Billing UI/launcher/support source files retain0hash delta, while
new Billing surfaces have their separate current native and packaged-pair proof.
Do not rerun unchanged old paths or relabel manual Figma reviews:0manualPASS/201PENDING,
owner comparison waiver explicit. Real payOS pilot remains NOT_READY until actual
provider/HTTPS/legal/operator/money prerequisites pass; that is not a local coding blocker.

Next action: Preserve final local evidence; wait for a separately authorized real pilot instead of repeating unchanged goal work.
Required from you: Nothing
Completion condition: Local acceptance and cleanup remain recorded; external release/provider completion needs actual authorized evidence.
