# Social content reapproval before restore

## Reproduced security defect

Approved plan 8.3 explicitly requires RESTORE after caption/media changes to obey
moderation policy. An actual issued OWNER/Admin JWT MockMvc sequence on disposable
PostgreSQL demonstrated the bypass with V2 authoring/previews enabled:

1. An ACTIVE item has reviewed content and merchant PRE_APPROVAL policy.
2. Admin HIDE commits, then Seller PATCH replaces both caption and READY gallery.
3. The edit remains private initially, but the existing restore command sets
   APPROVED without opening/requiring a new review.
4. Public preview returns the modified caption and replacement media URL.

Confirmed RED: `.local/social-v2-hidden-edit-restore-confirmed-red.log`.
Its failure includes the actual new caption and replacement media in public JSON.
An earlier attempt used nonexistent `/restore` rather than the real
`/restore-content` route; that 404 was a harness error and is not product evidence.

The same real sequence was checked for REJECTED, because the existing restore
domain method explicitly supports that state too. Actual REJECT→Seller edit→
RESTORE also exposed the new caption/media: RED in
`.local/social-v2-rejected-edit-restore-red.log`. Neither finding is a claim of
production exposure: these requests used disposable fixtures and test-only ON
flags. Normal V2/video flags remain OFF; activation/release stays blocked.

## Minimal policy-compatible correction

Only two production files change:

- `CatalogEditService`: for changed Social caption/media on ACTIVE/PAUSED items,
  include HIDDEN and REJECTED alongside the existing APPROVED/NOT_REQUIRED
  recheck. Only the existing `CatalogPublishPolicy` REQUIRE_MODERATION decision
  sends content to PENDING and opens/reuses the existing PRE_APPROVAL case.
  Identical Social payloads do not meet the changed-content predicate; flag-OFF
  legacy paths and other policy decisions retain their existing predicates.
- `CatalogModerationTargetHandler`: RESTORE requires the existing valid domain
  states HIDDEN/REJECTED. Other states, notably the new pending review, return
  existing `MODERATION_TARGET_STATE_CONFLICT`/409 before any mutation, rather
  than escaping as an IllegalStateException/500. Valid restore behavior remains
  unchanged. This invalid-state 409 also applies without the V2 flag; no new
  error code or response shape is introduced.

There is no new workflow/status, moderation role, permission, field, route,
schema/index or automatic approval. Explicit APPROVE uses the established
preapproval pipeline and its existing ACTIVE/PAUSED publication semantics. No
special new hidden-hold state or automatic case-history rewrite was invented.

## Targeted verification

The actual HIDDEN chain includes an unchanged-content control: HIDE→RESTORE
without editing still succeeds and returns the reviewed caption. A fresh case
hides it again; changing caption/gallery then creates PENDING with a real OPEN
PRE_APPROVAL case. Two attempts with the same restore key return 409 with the
known conflict code. The item row stays unchanged on denial; no RESTORE action,
RESTORE audit or RESTORE outbox payload is committed. Public batch omits the
item and public detail returns 404. Actual APPROVE and its replay then expose
only the new approved caption/media, with one approval action and unchanged
commerce offer row.

The REJECTED chain starts with a real Admin REJECT of a pending ACTIVE item,
then the actual Seller edit/RESTORE attempt. After the fix it remains PENDING,
RESTORE returns 409 and public preview omits the item. Explicit APPROVE through
the new case then returns the correct new caption/media; offer data is unchanged.

- After the HIDDEN correction: four focused tests PASS, Maven 38.023 s, in
  `.local/social-v2-hidden-edit-restore-green.log`: new HIDDEN chain plus existing
  ACTIVE caption, PAUSED caption/approval and flag-OFF patch regressions.
- After the separately reproduced REJECTED defect, the only further production
  change adds REJECTED to that state predicate. **Final two restore-chain tests
  PASS, 20.55 s; Maven 33.957 s, exit 0**,
  `.local/social-v2-social-restore-final.log`.
- The earlier four-test result predates that final predicate addition. The
  otherwise unchanged ACTIVE/PAUSED/OFF paths were not continuously rerun; the
  final run targets both affected restore states instead.
- Whitespace check PASS. Both tested production-file hashes match worktree
  sources. No DTO/API contract changes require client regeneration here.

All tests use actual issued JWTs, real servlet/security/services and PostgreSQL,
not mocked authorization or business outcomes. READY media are metadata fixtures
with test resolver URLs; this does not verify physical storage/CDN revocation or
browser UI. No unchanged edit/hide race, old full BE suite, frontend suite,
query-plan or generated-client gate was rerun as a substitute for these findings.

## Isolation and remaining gates

No recovered/runtime DB access, runtime migration/restart, normal feature flag
activation, remote change or deployment occurred. Normal web/API PIDs remain
unchanged. The new preview flag in the existing test class is test-context only;
normal V2/video defaults remain OFF.

These tests establish sequential reapproval safety for ACTIVE HIDDEN/REJECTED
fixtures, not every type/state/policy, simultaneous RESTORE/edit, in-flight
read/cache/CDN behavior, legacy flag-OFF media moderation, browser conflict UI,
notification delivery or staging/release. No staging exists and the whole Social
V2 objective remains active. The related production fix does not justify
refactoring the already-passed edit/hide optimistic locking path.

Next action: Verify the changed hidden-edit/RESTORE transaction paths under actual concurrent JWT requests in both commit orders.
Required from you: Nothing
Completion condition: A stale Restore or edit cannot expose new unapproved caption/media or commit partial gallery/case/audit/outbox state.
