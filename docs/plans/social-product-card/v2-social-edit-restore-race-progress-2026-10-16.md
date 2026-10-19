# Concurrent Social edit and RESTORE after the approval fix

## Actual requirement, not a repeated hide test

The preceding sequential tests reproduced and corrected a PRE_APPROVAL bypass
for edited HIDDEN/REJECTED content. This checkpoint tests those changed paths
under contention. The already-achieved edit/HIDE race and sequential restore
chains were not rerun as substitutes.

New `SocialEditRestoreConcurrencyIntegrationTest` uses actual issued OWNER/Admin
JWT MockMvc requests, independent transactions and disposable PostgreSQL. There
are no mocked permissions, business outcomes or repository responses. Historical
HIDE/REJECT cases/actions and ACTIVE hidden/rejected PRODUCT fixtures are seeded;
this test does not repeat their original moderation commands, run a browser or
prove network-wire/staging behavior.

One focused test executes four bounded schedules: HIDDEN and REJECTED, each with
Seller or Admin starting first. A separate FOR NO KEY UPDATE transaction holds
the item; pg_stat_activity must observe one, then BOTH actual item UPDATEs waiting
on locks before release. While both are blocked, public preview omits the item
and detail returns 404. Both actual commit outcomes are required to execute for
each starting state; this is test coverage, not a business priority guarantee.

## Committed observations

| Initial state | First command | Seller / Restore HTTP | Final item |
| --- | --- | --- | --- |
| HIDDEN | Seller edit | 204 / 409 | PENDING, new private caption/gallery |
| HIDDEN | Admin restore | 409 / 200 | APPROVED, exact original caption/gallery |
| REJECTED | Seller edit | 204 / 409 | PENDING, new private caption/gallery |
| REJECTED | Admin restore | 409 / 200 | APPROVED, exact original caption/gallery |

Every item finishes at version 1; the losing contended request returns existing
RESOURCE_VERSION_CONFLICT, not success or 500.

- Edit wins: new ordered gallery is replacement/old-first, positions 0/1 and
  primary true/false, with one OPEN PRE_APPROVAL and one opening outbox event.
  Public preview/detail stay private. A later restore attempt with the failed
  request's key still returns 409. No RESTORE action, audit or action-applied
  outbox event survives from the losing/retried command.
- Restore wins: public preview exposes only the exact OLD caption and BOTH old
  media IDs; public detail returns 200. The losing edit leaves no caption/gallery
  replacement, PRE_APPROVAL case or opening outbox event. Replaying the successful
  restore key returns 200 with the item row unchanged, and exactly one RESTORE
  action, one audit and one RESTORE outbox event.
- Other item fields (including commerce description/listing), complete offer,
  variant and owned asset rows remain unchanged. The original case remains
  RESOLVED; the tests do not claim all historical case columns were snapshotted.

Thus the fix does not disable valid restoration wholesale: concurrent valid
restore can publish the original content, but not the losing edit's new content.
Optimistic version checks and rollback also protect a restore that read the old
hidden/rejected state before the edit committed.

## Result and isolation

**One integration test PASS, four schedules, 29.91 seconds; Maven 42.393 seconds,
exit 0**, `.local/social-v2-edit-restore-race.log`. Whitespace check PASS. Both
production file hashes match the isolated build/current worktree. Existing fixed
BE code passes without further production changes, lock refactors, schemas,
indexes, DTO/API/client edits or added permissions.

No old full BE, hide-race, browser/component, OpenAPI/client generation or
typecheck gate was repeated. All writes/locks use disposable fixtures; no
recovered/runtime DB access, migration/restart, normal feature activation, remote
change or deployment occurred. Normal web/API PIDs remain unchanged. Normal
V2/video flags remain OFF; staging/release remain unverified and the whole
objective is active.

This is not every catalog type/listing/policy, in-flight public read or cache/CDN
race, legacy flag-OFF image moderation, actual storage bytes, browser conflict
UI, permission revocation or notification dispatch evidence.

## Next independent concurrency gate

Plan 8.3 separately requires author comment DELETE versus moderator HIDE. Source
review finds `CatalogSocialService.deleteComment` uses an atomic author/item/
ACTIVE-conditional UPDATE, while `SocialCommentModerationTargetHandler` takes
the comment row FOR UPDATE and validates its state. That suggests serialization
but is not a test result. Existing social test search found no actual contended
pair; inspect the concrete coverage before adding only the missing race. Do not
change its established 404/409 conflict behavior or refactor working code merely
because the two command implementations differ.

Next action: Verify author DELETE versus moderator HIDE with actual issued JWTs and controlled comment-row contention.
Required from you: Nothing
Completion condition: Both commit orders produce consistent nonpublic comment state and exactly the appropriate case/action/audit/outbox effects, without resurrecting a successfully deleted comment.
