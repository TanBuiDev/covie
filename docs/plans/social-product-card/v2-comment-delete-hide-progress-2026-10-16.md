# Social V2 — comment DELETE/HIDE contention and bulk conflict

## Scope and status

Plan section 8.3 requires consistent author DELETE versus moderator HIDE conflicts and audit. Existing social tests covered sequential ownership/visibility/direct HIDE/RESTORE, not concurrent issued-JWT commands. Whole V2 remains ACTIVE; normal V2/video flags remain OFF. No staging exists.

## Actual concurrent HTTP evidence

New `CommentDeleteHideConcurrencyIntegrationTest#authorDeleteAndModeratorHideHaveConsistentContendedOutcomes` uses actual issued author/reporter/Admin JWTs, MockMvc, disposable PostgreSQL, real comment creation/report HTTP calls, and no business/repository mocks.

An independent SELECT FOR UPDATE blocks the comment. Each ordering observes the first command waiting, starts the second, then observes both the conditional DELETE-status UPDATE and moderator SELECT FOR UPDATE waiting in pg_stat_activity before releasing the blocker. The committed ACTIVE comment remains publicly readable while blocked.

| First queued | DELETE | HIDE | Final comment | Case | HIDE action/audit/outbox |
| --- | --- | --- | --- | --- | --- |
| DELETE | 204 | 409 MODERATION_TARGET_STATE_CONFLICT | DELETED | OPEN | 0 each |
| HIDE | 404 RESOURCE_NOT_FOUND | 200 | HIDDEN | RESOLVED | Exactly 1 each |

Both schedules actually execute. Public comments, summary count and author COMMENTED activity exclude either final non-ACTIVE state. Full catalog item row and comment columns except status/updated_at stay unchanged. Same-key HIDE retry/replay preserves the committed comment and one-or-zero actions. Existing 404/409 semantics remain; no invented author deletion of HIDDEN comments or audit for a failed action.

Initial harness used `/hide`, not the actual `/hide-content`; only one lock waiter appeared. This is not product failure evidence. Correcting only the test route produced 1 PASS (19.27 s test, 33.028 s Maven) in `.local/social-v2-comment-delete-hide-race-final.log` with unchanged production code.

## Reproduced bulk defect and minimal fix

`ModerationBulkItemExecutor` already catches pre-mutation MODERATION_TARGET_STATE_CONFLICT and stores a per-item failure. But the comment handler's default transactional rule marked its joined transaction rollback-only before that catch could finish.

New `CatalogSocialControllerIntegrationTest#bulkHideRetainsDeletedCommentConflictWithoutAbortingOtherComments` creates/reports two comments, deletes the first through its author endpoint, then bulk-HIDEs both cases. Actual RED: expected 200, actual 500, with `UnexpectedRollbackException` at the bulk item transaction commit. Evidence: `.local/social-v2-comment-bulk-conflict-red.log`.

Only production change: `SocialCommentModerationTargetHandler.apply` now uses `@Transactional(noRollbackFor=ApiException.class)`, matching the established Merchant handler pattern. All explicit ApiException paths in this handler precede its UPDATE. Unknown runtime/database exceptions retain default rollback. No new locks, boundaries, statuses, permissions, API DTOs, generated clients or migrations.

The bulk test verifies ordered per-case conflict/success, unchanged deleted comment/item, second comment HIDDEN, cases OPEN/RESOLVED, zero versus exactly one HIDE action/audit/outbox, and exact same-body replay without duplicate effects.

## Final affected verification

Sequential isolated API run, source-synced files and disposable PostgreSQL:

```text
mvnw.cmd -o -Dtest=CatalogSocialControllerIntegrationTest#bulkHideRetainsDeletedCommentConflictWithoutAbortingOtherComments+reportsAndModeratesIndividualCommentsWithoutHidingProducts,CommentDeleteHideConcurrencyIntegrationTest test
```

- `.local/social-v2-comment-conflict-green.log`: 3 PASS, no failures/errors/skips, Maven 41.442 s.
- Includes new bulk regression, existing report/HIDE/RESTORE regression, and both contention schedules after the production annotation change.
- Source/private handler SHA-256 matches `1CA3759D0E784CEC0E1EB178B83B51351210649AE99BF87620D782583CD17597`.
- Targeted diff check passed. No full BE or unchanged catalog race rerun.
- Normal web/API PIDs 7544/25008 remain alive. No recovered/runtime DB access, restart, activation, commit/branch/remote changes or deployment.

## Limits and next gate

Controlled local PG/HTTP evidence is not browser UX, physical-device/load, CDN/in-flight invalidation, real outbox dispatch, staging, or proof for other moderation target handlers. G1–G6 remain partially unverified.

Next: audit section 8.3 Seller permission/merchant lock changes during caption/media transactions against existing source and coverage before any new test or behavior change.
