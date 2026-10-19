# Social Product Card 2.0 — paused caption moderation boundary

Partial local G2 verification/repair. The complete V2 objective remains active;
feature flags remain OFF and no staging/production deployment is claimed.

## Two demonstrated defects

1. `.local/social-v2-g2-paused-caption-red.log`: editing an approved PAUSED item
   under merchant-scoped PRE_APPROVAL retained APPROVED rather than PENDING.
   CatalogEditService rechecked only ACTIVE items; the existing activate command
   could therefore expose changed content without a new approval.
2. After extending that recheck to PAUSED,
   `.local/social-v2-g2-paused-approval-red.log` demonstrates that the actual Admin
   APPROVE command changed PAUSED to ACTIVE. CatalogItemEntity.approveModeration
   unconditionally activated the listing, contrary to preserving the Seller's
   paused state when the new Social content is approved.

## Minimal repair

- Material caption/media edits reevaluate the same CatalogPublishPolicy for both
  ACTIVE and PAUSED visible-moderation items. PRE_APPROVAL sets PENDING and uses the
  same transactional pre-approval case writer; no new moderation mode or endpoint.
- Approval preserves an existing PAUSED listing. Existing DRAFT approval still
  publishes, and existing ACTIVE approval still leaves ACTIVE. The Seller must
  explicitly activate a paused item after approval using the existing capability,
  publish-readiness and If-Match checks.
- No direct SQL state write, new domain status, API contract/role change or
  automatic activation was added. The pending activate denial remains the existing
  422 CATALOG_LIFECYCLE_INVALID, not a newly invented 409 response.

## Verification

`.local/social-v2-g2-paused-caption-green.log`: 3 tests pass, zero
failures/errors/skips, 27.872 s.

- New actual-issued JWT/PostgreSQL/HTTP chain: approved PAUSED item → caption PATCH
  under actual merchant PRE_APPROVAL → PAUSED/PENDING, ACTIVATE absent from server
  allowedActions, direct activation 422, public detail 404.
- Existing Admin case APPROVE with idempotent replay → APPROVED but still PAUSED,
  public still 404. Explicit Seller activation with the new ETag → ACTIVE and
  public detail 200. One moderation action/audit/outbox and the exact unchanged
  commerce offer row are asserted.
- Previously implemented ACTIVE caption regression reruns because its shared
  recheck condition changed; it still passes its persistence/commerce/moderation
  assertions.
- A small model regression confirms unchanged DRAFT/ACTIVE approval behavior.

Maven runs in the isolated `.local/social-v2-api-validation/api` copy with disposable
PostgreSQL. No API schema/client changed, so unchanged schema/export/typecheck gates
were not rerun. No whole BE run, migration, recovered-data write, original service
restart, feature activation, commit or deployment occurred.

## Still unverified

This is sequential pause/edit/approve/activate evidence, not proof of concurrent
approval/edit/pause races or every moderation mode/target. Caption authorization
revocation, representative V019→V020 data upgrade, Seller Studio, media limits/order,
Admin caption readback, G1 browser/query/storage privacy and G3–G6 remain in scope.
No staging exists. The V2 goal is not complete.
