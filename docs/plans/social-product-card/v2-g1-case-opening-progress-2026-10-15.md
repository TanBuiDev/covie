# Social Product Card 2.0 — G1 Admin case opening

Owner approval covers this contract and all separate-approval requirements in the
V2 implementation plan. Do not ask again for those approvals. Approval does not
replace technical evidence or authorize bypassing safe rollout gates.

## Implemented locally

- POST `/api/v1/admin/catalog-items/{itemId}/moderation-cases` requires Admin,
  reason/note and Idempotency-Key. Independent
  `covie.social-v2.admin-case-opening-enabled` defaults to false. The flag is
  checked before starting idempotency processing; enabling reads does not enable
  this command.
- The existing catalog-item lock serializes Admin opening commands. Reuse the
  oldest OPEN case deterministically. Otherwise create SYSTEM_FLAG only for
  NOT_REQUIRED/APPROVED items. Deleted/missing items return 404; incompatible
  moderation state returns 409.
- Store reason/note, one `ADMIN_MODERATION_CASE_OPENED` audit event and one
  `MODERATION_CASE_OPENED` outbox event atomically. Opening does not mutate item
  state. Existing HIDE/RESTORE commands remain unchanged.
- Response `{caseId,created}` uses the actual case. Replayed requests preserve
  the original result; a different request reusing an OPEN case returns false.
- OpenAPI and generated client updated additively. Semantic comparison confirms
  all pre-V2 operations and schemas remain unchanged; client typecheck passes.

## Verified evidence

- RED: `.local/social-v2-g1-case-opening-red.log`, expected POST 200 but received
  405 because the command did not exist.
- GREEN: `.local/social-v2-g1-case-opening-green.log`, 8 tests pass in 32.650 s,
  including existing Admin reads, real moderation chain, disabled flags and
  OpenAPI export. The new command regression uses issued JWTs and PostgreSQL:
  creation/replay/reuse/body mismatch, exact unchanged item row, case/audit/outbox
  counts, Admin read visibility, guest/non-Admin denial and existing HIDE flow.
- `.local/social-v2-g1-case-opening-guards.log` separately records 1 passing test
  in 25.084 s after extending only the command regression with missing key 400,
  blank reason 422, nonexistent item 404, zero cases before valid creation and
  exact persisted note. Unchanged read/chain tests were not rerun for that extension.
- The earlier isolated real report→HIDE→RESTORE chain passes in
  `.local/social-v2-g1-moderation-chain.log` (1 test, 33.332 s). It verifies public
  visibility, owner reads, replay, preserved offer/description and actual outbox
  processing with one owner notification per moderation action. No production
  behavior change was needed for that existing chain.

All backend builds run in `.local/social-v2-api-validation/api` with disposable
PostgreSQL. No recovered-data write, migration, original API restart, commit,
PR merge, staging deployment or production deployment occurs here.

## Concurrent Admin opening

`AdminCatalogReadIntegrationTest#concurrentAdminsReuseOneCaseWithoutMutatingItem`
uses two independently issued Admin JWTs and two independent Idempotency-Keys.
A separate PostgreSQL connection initially holds the target item row lock. The
test requires both HTTP commands to be observed in `pg_stat_activity` waiting on
catalog-item locks before releasing it; this is not merely simultaneous thread
submission. Both requests return 200 and the same case ID, with exactly one
`created=true` and one `created=false`. SQL verifies one case, one opening audit,
one opening outbox event, and an exact unchanged catalog item row.

`.local/social-v2-g1-case-opening-concurrency.log`: one test passes, zero
failures/errors/skips, 24.858 s. No production code change was necessary. This
proves the two-Admin opening race only; report-vs-opening, HIDE-vs-opening and load
behavior are not claimed. Previously passed unchanged tests were not rerun.

## Still incomplete

The opening UI reason/confirmation, conflict reconciliation and actual case
navigation are now implemented locally; see
`v2-g1-case-opening-ui-progress-2026-10-15.md` for component evidence and remaining
real-browser gaps. The two-Admin opening race is verified as described above.
Query-plan, storage privacy,
responsive/browser and staging/release evidence remain unverified. G1 is partial;
G2–G6 and the full V2 objective remain active. Features stay OFF.
