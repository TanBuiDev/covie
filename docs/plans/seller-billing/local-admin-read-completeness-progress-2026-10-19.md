# Admin publication history and subscription lookup — local progress

Whole goal ACTIVE / NOT_READY. These are concrete original4.1/10.2/10.4 dashboard
gaps found beyond the original30BIL rows; no new financial decision or real payment.
Narrow reviewed plan: `docs/superpowers/plans/2026-10-19-billing-admin-read-completeness.md`.

## Implemented, not yet whole-goal accepted

- Admin-only/no-store bounded GET price/policy history over existing immutable
  records, ordered revision DESC. Price history includes retired plans and preserves
  stored publication versus effective timestamps. No edit/restore action. UI uses
  generated records and independent pagination, explicit loading/empty/error/retry,
  keyboard-focusable table and guards delayed plan-switch responses.
- Existing finance-reader subscription GET gains optional exact merchantId filtering
  and an additive currentOwnerUserIds array: distinct sorted ACTIVE OWNER membership
  UUIDs only. It does not expose name/email/phone or claim active user accounts,
  immutable ownership or historical ownership. Correlated aggregate preserves one
  row per subscription, existing bounds/default order/deleted-merchant exclusion.
- Read-only Admin lookup shows stored subscription fields, current memberships and
  independently paginated associated invoices using the existing scoped invoice GET.
  Delayed merchant responses are discarded, old records clear on input changes.
  Current state/invoices are explicitly NOT a complete state-transition timeline;
  absent older optional OWNER data is unknown, not falsely an empty membership list.
- Existing publication, quote, invoice, settlement, grant, moderation, eligibility,
  owner billing permissions and finance-role configuration remain unchanged. No SQL
  migration, dependency, new money action, production flag or recovered DB operation.

## Executed evidence

- History HTTP actual RED404, `.local/billing-admin-history-red.log`1FAIL61s.
  First implementation run has history fixture failure from comparing9-digit request
  nanoseconds against PostgreSQL stored microseconds, while official OpenAPI2PASS.
  Only the new fixture timestamp was truncated to millis; publication API unchanged.
  `.local/billing-admin-history-final.log`:1PASS23.93s/Maven59.589s, actualPG/issuedJWT,
  retired2-version pagination, stored timestamps, Admin/401/403/422/404, eleven full
  financial/config/audit/outbox table snapshots unchanged, provider creation0.
- History component module absence RED before implementation; affected history+parent
  UI13PASS2.30s; typecheck/scoped lint exit0. Canonical diff exactly two history GETs
  and two schemas,178paths235schemas; no existing operation/schema change at that step.
- Subscription HTTP actual missing owner-array RED, then
  `.local/billing-admin-subscription-read-green-contract.log`:3PASS0skip73s,
  one actualPG/issuedJWT read case plus two official OpenAPI cases. Multiple ACTIVE
  owners do not duplicate rows; INVITED/REVOKED/MANAGER excluded, ownerless empty,
  exact filter/offset/deleted/unknown/permissions/bounds and full snapshot/provider0.
- Subscription component absence RED, affected16PASS2.75s. New optional-field
  compatibility assertion RED then corrected display; final three components
  `.local/billing-admin-read-ui-final.log`:17PASS2.12s. Official generate/check and
  typecheck exit0; scoped lint0; production Next buildexit0/38routes in
  `.local/billing-admin-read-build.log`. This is not a new native/browser claim.

## Current executions and remaining gates

Actual current Spring/PG/production Next/Chrome fixture6189 is running
`.local/billing-admin-read-native.log` with `admin-reads` scenario. It prepares a
retired version history and three fake-provider-paid subscriptions using existing
helpers, then the browser only reads at360/390/1366. It must prove native table
keyboard scrolling, current owner membership, associated invoices, no browser writes,
eleven-table equality and no page errors; no PASS is predicted.

Full Web80393 is running `.local/billing-admin-read-web-full.log`, not yet PASS.
Prior546PASS12RedisSKIP,17changed cases and prior unchanged Redis12PASS are distinct
runs. Prior exact CurrentDesign UI command at23e2c104bdcf was genuinely PASS and
cleaned, but predates these new Admin source files. Current packaged3CAFC093... is
historical for these new API reads; no new package/JVM/security result yet.

Full BE is deliberately deferred until new local read source/native fixtures are
stable, rather than repeatedly running unchanged financial/migration suites. Final
whole-numbered-plan matrix, source/artifact scan, current packaging and exact UI
source provenance remain due. Real provider/HTTPS/legal/on-call/money/release gates
remain external; all normal flags OFF, no remote write/commit/branch/deploy/data use.

## Final local closure

The running-state paragraphs above are historical. Full BE94985 exit0 at18:27:49:
1070reported/1069PASS/1HotQuerySKIP/0failures/errors9m06s, independent279suite XML.
Final current Web553PASS12RedisSKIP94.35s; typecheck/full lint/build exit0 with28existing
warnings0errors and38routes. Canonical178paths235schemas exactly equals full export.

Initial native read1PASS63.18s exposed new8-column subscription text fragmentation
at1366 in its viewed screenshot; this is a new-component defect, not a reason to
refactor existing tables. Focused style RED → only new subscription table nowrap
inside the existing keyboard-focusable scroller. Existing audit navigation was reused
with actual invoice IDs and accurate lifecycle/invoice scope instructions, not a new
state-history service. First associated-invoice unit lookup then failed its stale
exact-text assumption after ID display; test-only regex adjustment, final4PASS1.48s.

Final native4646 exit0:1PASS33.69s/Maven37.920s,
`.local/billing-admin-read-native-layout-final.log`, fixture1792409496242 completedTRUE.
360/390/1366 regions remain286/316/984wide, subscription natural table1371/1371/1366;
native Tab/ArrowRight await assertions prove scrolling, text Range checks prove
plan/status/OWNER stay one line, document remains bounded. geometry.left was sampled
before animation, so its0 is not claimed as a post-scroll value. Three final screens
VIEWED; browser0writes/0errors, eleven tables fullJSONB same. Actual existing audit
query for the associated invoice returns200 and exactly one settlement event.

Current final packaged JAR BFA190DC... plus Next build lAC_u_y7plWCBPzSYws9H booted on
fresh owned PG18/Redis8/generatedRSA with real login: three history/subscription GETs
200/no-store, six financial tables unchanged, browser0writes after login/0errors,
three widths bounded. Fixture00b4a04c6992 result/cleanupTRUE, keys/processes/containers
removed. This is a focused current packaged-pair read proof, not a repeated money or
process-kill scenario. Trivy currentJAR186Java packages0HIGH/CRITICAL/networkNONE;
production pnpm advisory scan0. Full source inventory2077files/default Gitleaks raw7
matches are all reviewed unchanged noncredential documentation;0unresolved, no ignore
or detector change. See `local-final-acceptance-2026-10-19.md` and exact receipts.

Local implementation/verification is closed; real payOS pilot remains NOT_READY.
No other local approval, staging prerequisite or speculative feature expansion is needed.

Next action: Preserve local evidence; only begin a real pilot on a separate authorized request with verified prerequisites.
Required from you: Nothing
Completion condition: Local proof and cleanup are recorded; real provider/release claims require actual separately authorized evidence.
