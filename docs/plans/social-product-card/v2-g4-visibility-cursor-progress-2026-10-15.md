# Social Product Card 2.0 — G4 visibility and comment cursor evidence

## Result

Existing G4 production code passed both new targeted checks. No production fix,
refactor, new index, migration or API change was justified. This advances the
privacy/cursor evidence in `v2-g4-activity-backend-progress-2026-10-15.md`, not
the whole G4/UI or release gate.

## Visibility matrix

`SocialActivityIntegrationTest.everyActivityTypeExcludesRestrictedCatalogMerchantAndCommentRowsWithoutWriting`
uses an actual issued account JWT, MockMvc security/controller/service and
disposable PostgreSQL. Sixteen catalog fixtures have both reaction kinds and
an authored ACTIVE comment. Each of LIKED, SAVED and COMMENTED returns exactly
the same two eligible item IDs:

| Fixture group | States covered | Expected |
| --- | --- | --- |
| Public controls | Item/merchant NOT_REQUIRED; item/merchant APPROVED | Included |
| Item publication | DRAFT, PAUSED, DELETED | Excluded |
| Item removal | deleted_at set while otherwise public | Excluded |
| Item moderation | PENDING, REJECTED, HIDDEN | Excluded |
| Merchant publication | PAUSED, SUSPENDED, DELETED | Excluded |
| Merchant removal | deleted_at set while otherwise public | Excluded |
| Merchant moderation | PENDING, REJECTED, HIDDEN | Excluded |
| Comments on eligible item | HIDDEN and DELETED | Excluded from COMMENTED |

The current listing model has no ARCHIVED enum. Its archived/deleted presentation
uses DELETED; the test does not invent another state. State fixtures are written
with explicit SQL after constructing real catalog/merchant entities. They prove
read-policy behavior for those persisted states, not a new end-to-end Admin
moderation transition chain.

Restricted item, merchant and comment rows have distinctive marker strings.
None appears anywhere in the response; no false continuation cursor is returned
after the two eligible rows. Canonical JSONB snapshots of **all columns** in
catalog items, merchants, reactions and comments stay exactly unchanged across
the three authenticated reads. Auth/session infrastructure is outside this
business-row snapshot scope.

`.local/social-v2-g4-visibility-matrix.log`: **1 test passes, 25.040 s**, zero
failures/errors/skips. Other unchanged G4 tests were not rerun for this check.

## Comment keyset continuation under sequential changes

`commentCursorSurvivesDeletedAnchorAndFiltersHiddenRowsAndNewerInserts` creates
25 owned ACTIVE comments on one visible item, all at the same timestamp:

1. Read first ten in exact `(created_at, comment_id)` descending order.
2. Mark the already-returned cursor anchor DELETED. Hide one as-yet-unseen row.
3. Insert a newer owned comment and a foreign account's **older** comment. Making
   the foreign row older ensures it is not accidentally excluded only because
   it lies above the cursor.
4. Follow the original cursor: next pages contain exactly ten then four rows,
   followed by exhaustion. Combined IDs preserve expected order with no duplicate,
   hidden, newly inserted above-cursor or foreign comment.
5. The foreign account's fresh COMMENTED request sees exactly its own comment.
   Business snapshots stay unchanged by all continuation/foreign reads.

The previously returned anchor remains part of the recorded first page; deleting
it later does not retroactively change that earlier HTTP response. This is not
a cross-request snapshot guarantee, nor proof that an eventual activity UI
automatically removes already-rendered deleted rows. Refresh/reconciliation UI
behavior still needs its own implementation and evidence.

Initial focused execution passed in 24.624 s. The fixture was then strengthened
with the older foreign row and explicit foreign-account read; the final
`.local/social-v2-g4-comment-cursor-final.log` records **1 pass, 24.738 s**, zero
failures/errors/skips. Only this modified method was rerun, not the earlier
unchanged visibility matrix or complete BE suite.

## Limits and next work

Both checks are sequential. They do not prove simultaneous hide/revoke/read,
reaction recreation concurrency, arbitrary browser account switching, live media
object privacy or cache invalidation. G4 exact query plans/counts, representative
load and private activity UI/profile entry remain unfinished. G1–G3 remaining
technical/browser gates and G5/G6 requirements remain open as previously recorded.

All tests ran sequentially in the isolated API copy and disposable PostgreSQL.
No recovered-data write, running-service restart, environment edit, feature
activation, commit or deployment occurred. Since production/API/client sources
did not change, their previous generation/typecheck gates were not repeated.
There is no staging; flags stay OFF and the full goal remains active.
