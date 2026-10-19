# Social Product Card 2.0 — caption authorization evidence

No production code change was needed. The existing persisted merchant access
policy is correct for the new caption field and remains unchanged.

## Actual targeted verification

- `.local/social-v2-g2-caption-authorization.log`: one test passes, zero
  failures/errors/skips, 25.112 s. Issued MANAGER JWT writes caption successfully.
  An OWNER of a different merchant cannot PATCH the item through either the real
  merchant route or an alias using their own merchant ID: both return 404 with the
  exact item row unchanged. Assigned STAFF receives 403 for this structural edit.
  Revoking the manager's persisted membership causes the same already-issued JWT
  to receive 404 on the next caption PATCH. Item, variant, offer and media rows
  remain exactly unchanged after denied requests.
- `.local/social-v2-g2-caption-create-revocation.log`: one test passes, zero
  failures/errors/skips, 28.198 s. Issued OWNER JWT creates a caption item, then its
  persisted membership is revoked. The same token cannot replay the existing
  create key or execute a new create key (both 404). Item count, exact original
  item/offer rows and the single creation outbox event remain unchanged.

Both cases use actual JWT issuer/security, HTTP MockMvc routing, services,
repositories and disposable PostgreSQL. Merchant authorization is not mocked.
Only the two new cases were run; previously passed unchanged gates were not rerun.
Builds use the isolated `.local/social-v2-api-validation/api` copy. No recovered
database write, original service restart, migration, API/schema change, feature
activation, commit or deployment occurred in this step.

## Scope limitations and next gate

This proves next-request membership revocation, not a concurrent revoke/write race
or live browser session behavior. It does not replace the remaining caption/media
concurrency, Seller UI, Admin caption readback, gallery/order/limit and migration
checks. G1 browser/query/storage-privacy and G3–G6 remain unfinished. Flags stay OFF;
no staging exists and the complete V2 objective remains active.

V019→V020 preservation is subsequently verified on populated disposable data;
see `v2-g2-caption-migration-progress-2026-10-15.md`. The previous hardcoded
latest-V019 expectation is repaired in that gate. This does not supply missing
staging/production upgrade evidence.
