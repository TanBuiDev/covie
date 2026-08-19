# Task 10 report

## Delivered

- Idempotent `POST /api/v1/merchants/{merchantId}/catalog-items` creates one DRAFT aggregate in the idempotency transaction.
- Validates merchant access/state, category compatibility, typed category attributes, option/variant client keys and canonical combinations, owned READY `CATALOG_IMAGE` media, and location-offer invariants.
- Creates the server default variant exactly once when variants/options are absent; exposes it with `name: null` and `isDefault: true`.
- Creates tags, media links, option groups/values, variants, joins, and location variants; then rebuilds the weighted search vector and appends `CATALOG_ITEM_CREATED` to the outbox.
- Corrected an order-dependent persistence assertion to assert the categories created by that test, rather than all categories in the shared integration database.

## Verification

- `mvnw.cmd -Dtest=CatalogCreateServiceIntegrationTest,MerchantCatalogCreateControllerIntegrationTest test` — 7 tests passed.
- `mvnw.cmd test` — 207 tests passed, 0 failures, 0 errors.

## Notes

- JDK/Testcontainers/Mockito emit existing runtime warnings; no catalog test failures remain.

## Review round 1

- Idempotency replay now performs current membership capability and ACTIVE-merchant checks before looking up a stored response; the transactional creation callback retains its locked recheck for race safety.
- Slugs now bound their normalized base before the UUID suffix so valid 220-character names fit `catalog_items.slug varchar(240)`.
- Tags over 100 characters now produce `VALIDATION_FAILED` before persistence.
- The aggregate is flushed before the JDBC search-vector rebuild, so a committed item has its weighted `search_vector`.
- Regression coverage adds revoked/demoted replay, 205/220-character names, overlong tags, default-variant/no-option-join/search-vector persistence, and explicit negative cases for duplicate/unknown client keys, offer variants, foreign/disabled locations, service inventory, and non-READY media.

## Review round 1 verification

- `mvnw.cmd -Dtest=MerchantCatalogCreateControllerIntegrationTest,CatalogCreateServiceIntegrationTest test` — 11 tests passed.
- `mvnw.cmd test` — 211 tests passed, 0 failures, 0 errors.
