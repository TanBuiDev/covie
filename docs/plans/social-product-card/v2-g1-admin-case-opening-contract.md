# G1 — Admin case opening contract for approval

Status: APPROVED by the owner on 2026-10-15. The owner also explicitly approves
all separate-approval requirements in the V2 file; do not ask again for those
approvals. Technical evidence/availability and safe rollout gates still apply.
This describes the implementation contract. The V2 plan G1.2 requires an audited/idempotent flow
when an Admin needs to intervene in an item without a valid existing case.

## Proposed contract

`POST /api/v1/admin/catalog-items/{itemId}/moderation-cases`

- Require AdminAccessPolicy and an independent default-OFF
  `covie.social-v2.admin-case-opening-enabled` flag. Enabling reads alone must not
  enable this write. No new system/merchant role is granted.
- Required Idempotency-Key; reuse the existing IdempotencyExecutor with actor +
  operation + item scope. Body uses existing AdminReasonRequest: reasonCode
  nonblank/max 60, optional note/max 2000.
- Response 200: `{caseId, created}`. `created=false` means a current OPEN case
  was reused; the UI navigates to that actual case. Replay returns the same result.
- Lock the catalog item using the existing CatalogItemRepository.lockById in the
  transaction. If an OPEN catalog-target case exists, reuse it deterministically;
  do not create another case just because the Admin clicked from a different page.
- If no case exists, create an OPEN SYSTEM_FLAG targeting this item using the
  existing case type/schema. Do not pretend that the Admin action is a USER_REPORT
  or PRE_APPROVAL. Record reason/note and an explicit Admin case-open audit event;
  append the existing MODERATION_CASE_OPENED outbox event in the same transaction.
- Only open a new case where the existing target handler/model permits a HIDE
  action (moderation NOT_REQUIRED or APPROVED); deleted/nonexistent target 404,
  incompatible target state 409. Existing case workflows remain authoritative.
- Opening the case does not change item/merchant status, listing ownership,
  caption/media, offers, stock or orders. HIDE/RESTORE/CLOSE remain commands on the
  existing `/admin/moderation-cases/{caseId}/...` pipeline, with its reason,
  idempotency, audit/outbox and conflict handling.
- No migration, permission expansion, direct SQL status write or public endpoint.

## Required verification if approved

One targeted PostgreSQL/JWT command regression: real Admin opens a case, replay
does not duplicate case/audit/outbox, independent repeated request reuses OPEN
case, item/offer rows remain unchanged, case is visible through G1 reads, and its
existing HIDE command still applies through audited moderation. Also verify flag
OFF and guest/non-Admin denial using existing focused fixtures.

UI shows an explicit reason/confirmation before sending. It does not show a fake
case or infer a successful status change. On conflict, refresh authoritative item
and case data. No opening control is displayed while its separate flag is OFF.

## Alternative if not approved

Keep the current read-only no-case state and existing report/moderation navigation.
Preserve the remaining gap explicitly: an Admin cannot directly start a case for
unreported content from the new listing UI. Do not label that alternative full G1
intervention completion.
