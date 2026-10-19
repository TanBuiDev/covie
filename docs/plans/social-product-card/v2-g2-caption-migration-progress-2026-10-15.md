# Social Product Card 2.0 — populated caption migration evidence

V020 is verified locally on populated disposable PostgreSQL. It is not deployed
to the recovered database, staging or production. The complete V2 goal remains
active and feature flags remain OFF.

## Actual RED and repair

`.local/social-v2-g2-migration-current-red.log`: the existing populated migration
gate fails because it expects six migrations after V013, but the new V020 makes
seven. This is a stale test expectation caused by the newly added migration, not
a demonstrated production migration failure.

The existing test now explicitly migrates V013→V019 (six migrations), populates
Catalog/Social data at V019, and migrates V019→V020 (exactly one migration). The
previous User/Address/Order/Service fulfillment preservation assertions remain.
No production SQL or earlier migration checksum was changed in this step.

## Verification scope

`PopulatedMigrationUpgradeTest#upgradesV013UserAndAddressDataToLatestWithoutChangingExistingValues`
seeds the following real rows before V020:

- PRODUCT/STANDARD ACTIVE/APPROVED, SERVICE/STANDARD PAUSED/APPROVED, and
  PRODUCT/RESALE DRAFT/NOT_REQUIRED, with existing descriptions/search vectors,
  identities, ownership and nonzero versions.
- Default variants, 31000 VND offers, TRACKED/null/UNTRACKED modes as appropriate,
  a tracked balance (stock 5/reserved 2), READY media and gallery ordering/primary
  associations. Gallery sort orders deliberately retain an existing gap (0,5).
- Existing Like/Save and an ACTIVE comment; the earlier fixture already contains
  User/Address/Merchant/Location/Order/Service fulfillment rows.

Before and after V020, the test snapshots every business table in the public
schema as ordered canonical JSONB row values, excluding only Flyway history and
the new caption property from catalog comparison. Exact snapshots match, proving
both row counts and old field values are unchanged for the seeded data.

The new column is verified as nullable text with no default; all three existing
items have null caption. The catalog relation's relfilenode remains unchanged,
providing local evidence that this ALTER did not rewrite its heap. Flyway validate
passes at V020, and reapplying executes zero migrations. The original pre-V013
field-specific preservation checks also pass.

`.local/social-v2-g2-migration-populated-green.log`: one test passes, zero
failures/errors/skips, 18.270 s. Measured V019→V020 Flyway migration call: **64 ms**
on this small local fixture. This is not a production latency estimate, staging
benchmark, lock-contention/zero-downtime proof or recovered-data acceptance test.

The build runs in `.local/social-v2-api-validation/api`; the test starts and
disposes its own `postgis/postgis:18-3.6` container. No original application
restart, recovered-data write, feature activation, commit or deployment occurred.
Unchanged backend business/API/UI gates were not rerun.

## Remaining full objective

G2 still needs Seller Studio, media limits/order/preview, Admin caption readback
and caption/moderation concurrency evidence. Real staging migration duration and
lock behavior require representative data and infrastructure not yet available.
G1 browser/query/storage privacy and G3–G6 remain in the original scope; no staging
exists, and release remains unverified.
