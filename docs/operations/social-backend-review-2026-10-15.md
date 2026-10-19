# Social Product Card backend review — 2026-10-15

## Synchronization and scope

Fetched origin and fast-forwarded the existing local branch from `f30a313f` to
`803b25b89f4686b4135b0de5ef101aabcc84b614`, the current `origin/main` containing
Social Product Card Phase 1–4. Existing uncommitted catalog/barcode and operations
changes were retained. No commit, push, application deployment or live database
migration was performed.

Reviewed the incoming social controllers/service, V018/V019, comment moderation
target, report visibility, case persistence and author notification branch, against
the FE social API/thread/feed and existing JWT/session, CSRF and rate-limit chain.
Existing checkout/order behavior was not changed or retested merely because social
code was added. This review does not replace historical whole-backend evidence or
claim a fresh full-backend regression run.

## Confirmed defect and minimal repair

The FE retains `nextCursor` after deleting a comment. The backend's cursor lookup
required that comment to remain `ACTIVE`, so deleting or moderating the cursor
comment made subsequent pages empty while older active comments still existed.

The repair removes only the cursor anchor's status predicate. Returned comments
still require `ACTIVE`; the anchor still belongs to the requested catalog item;
item/merchant visibility and UUID cursor/API contracts are unchanged. No hidden or
deleted comment body is returned. V018/V019 remain unchanged.

One focused regression reproduces both author deletion and moderator hiding of a
page anchor using PostgreSQL, actual issued JWT/HTTP deletion and the real moderation
handler. It checks ordered continuation and terminal pagination.

## Verification evidence

| Check | Current evidence |
| --- | --- |
| Reproduction before repair | `.local/social-cursor-red-20261015.log`: expected one next-page comment, received zero |
| Social HTTP/JWT/PostgreSQL suite after repair | `.local/social-cursor-green-20261015.log`: 6 tests, zero failures/errors/skips; 35.180 s |
| Existing moderation acceptance/model/command suites | `.local/social-backend-focused-20261015.log`: 21 + 1 + 2 tests pass |
| Populated migration upgrade through V019 | Same focused log: 1 test passes, existing user/address/order/fulfillment data preserved and reapply executes zero migrations |
| OpenAPI export | `.local/social-openapi-export-20261015.log`: 2 tests pass; exported `.local/social-openapi-20261015.json` semantically equals committed OpenAPI after recursively sorting object keys, preserving array order |
| FE social/view/home regression | `.local/social-web-focused-20261015.log`: 4 files / 14 tests pass |
| Web typecheck | `.local/social-web-typecheck-20261015.log`: exit 0 |
| API client typecheck | `pnpm api-client:check`: exit 0 |
| Existing social load scenario definitions | `node --test infra/perf/social-scenarios.test.mjs`: 3 tests pass; no actual load measurement implied |

The first post-repair combined run had one regression assertion failure because the
existing JSON serializer omits a null terminal cursor. The test assertion was
corrected to accept absent/null; the production serializer/API was not changed.
The subsequent social suite passes. Other 25 passing backend checks were not rerun
because their relevant code had not changed. Together with OpenAPI, 33 distinct
backend tests have passing results; there is no claim that the initial combined
command succeeded.

## Deployment evidence boundary

The user confirmed that staging is not available and requested recording the
unverified portion. Responsive browser acceptance, actual social p95/error rate,
manual admin/CSRF workflow, staging P0 regression and deployed-image backup/restore/
rollback rehearsal remain unverified. Follow the existing Phase 4 runbook when
staging is provisioned. Local checks establish the stated code/contract behavior;
they do not establish production rollout acceptance.
