# Admin publication preview — local implementation and evidence

Whole Seller Billing goal remains ACTIVE / NOT_READY pending final stable gates.
This closes an actual original4.2.5 implementation gap, not a new pricing policy.
No real payOS request, money, activation, deployment, remote write or recovered-data use.

## Narrow changes

- New Admin-only, no-store GET `/api/v1/admin/billing/plans` and
  `/api/v1/admin/billing/policy/latest` show latest published revisions, including
  scheduled versions. Public effective-now plans and existing effective-now policy
  GET are unchanged. This avoids stale revisions after a future publication.
- New POST `plans/{planCode}/prices/preview` and `policy/preview` under the same Admin
  billing prefix reuse existing change bodies and quoted If-Match. They validate
  without locks/mutations, return previous/proposed values and server observation time;
  actual publication still independently checks its existing revision and row lock.
- The population is explicitly **current subscription records, all statuses**: for
  selected plan or global policy. Cancelled/expired/revoked records are included;
  merchants without subscriptions are excluded. It is an advisory reference cohort,
  not an exact forecast of eligibility, charges or immediately affected merchants.
  Policy changes do not rewrite already snapshotted trial/grace or paid periods.
- Existing Admin confirmation now follows a successful server preview, shows old/new
  values, date/cohort/limits and publishes the same captured body/revision. Cancel
  does not publish. Preview/publication409 preserves the draft and requires explicit
  reload/review rather than silently rebasing. No new dependency, migration or money path.

## Executed targeted evidence

1. `.local/billing-publication-preview-red.log`: actual issued-JWT/PostgreSQL18 HTTP
   three failures, missing preview404 and missing Admin GET405. No production code
   existed for those operations. New fixture creation itself succeeded.
2. `.local/billing-publication-preview-green.log`: five executed, four PASS; the one
   failure was our new anonymous POST expectation401 versus existing CSRF403. Source
   `BrowserCsrfRequestMatcher` confirms anonymous unsafe methods require CSRF.
   Only that assertion changed; no authentication/CSRF relaxation. The populated
   preview case proves unchanged full JSONB for six financial tables plus plans,
   plan/policy versions, audit and outbox, and provider creation count0. Both existing
   real PG-lock concurrent publication regressions passed unchanged.
3. `.local/billing-publication-preview-final-contract.log`: four PASS0skip,
   100s: the corrected denial case, two consecutive future price/policy revisions
   with effective-now reads unchanged, and two official OpenAPI export cases.
   Scheduled policy fixture removes only its newly created versions/events in its
   disposable DB after assertions, so later tests do not inherit future schedules.
   This is not a fictional five/seven-case combined green run.
4. Initial UI attempt failed an incorrect new label locator and is not product RED.
   Corrected `.local/billing-publication-preview-ui-red-verified.log` confirms the
   original generic confirmation lacks the server cohort. Some following failures
   also inherited an unrestored confirm spy; beforeEach now restores mocks.
   `.local/billing-publication-preview-ui-green.log`: nine PASS2.06s, including
   price/policy cancellation, delayed-preview field edits/captured body and final409,
   and stale preview never confirming. Subsequent typecheck caught unchecked indices
   in only the new test; optional access corrected, no production workaround.
5. Official client generate/check, final Web typecheck/scoped lint and production
   build exit0. Canonical176paths/233schemas. Compared with the independently hashed
   prior canonical65CF69DD..., semantic diff is exactly four additive operations
   and two response schemas; no old operation/field/schema change:
   `.local/billing-publication-contract-semantic-diff-final.log`.

## In progress, not claimed PASS

Native pricing acceptance now checks cancel/read-only, stale preview409, explicit
reload of scheduled revision and policy cancellation at360/390/1366 before the
existing fake-provider checkout/settlement assertions. Actual execution/package
is running in `.local/billing-publication-native-pricing.log`. Stable full Web is
running in `.local/billing-publication-final-web-full.log`. Final BE, owned exact
CurrentDesign UI command, source/artifact scans and whole-plan matrix remain due.
Normal release/enforcement/reconciliation/money flags stay OFF.

## Final local closure

Native pricing93945 finished exit0:1PASS117.7s/Maven168s. Actual fixture1792407069181
at360/390/1366 proves cancel preserves revision1, stale preview409, explicit reload
scheduled revision2, policy cancellation preserves revision and three fake-paid
checkout/settlement records. Later meaningful label/date RED → focused10PASS and
actual current-label three-width read proof; ISO business values remain unchanged.

Exact owned CurrentDesign command23e2c104bdcf exited0 across all journeys/201automated
captures/81cases and cleaned. Its manual comparison remains waived/unperformed, not
201reviews. First full attemptcf97 failed a loading-capture timing race, not missing
product loading: trace showed10s document latency with already-ready content. A test-only
preparse MutationObserver freezes the actual six-skeleton fallback before later
evaluation misses it; focusedPublic1PASS3.5s and fullPublic1PASS14.1s, screenshotVIEWED.
No application loader/service-worker behavior was changed for that capture.

Subsequent confirmed original-plan Admin history/subscription gaps were completed
separately, not silently excluded. Final full BE1069PASS+1skip9m06s, Web553PASS+12skip,
current native/new packaged-pair/security evidence and exact limitations are in
`local-final-acceptance-2026-10-19.md`. No remote write, money, activation or recovered data.

Next action: Preserve final local evidence; do not repeat unchanged preview work or activate flags.
Required from you: Nothing
Completion condition: Local acceptance remains recorded; any real pilot is separately authorized and verified.
