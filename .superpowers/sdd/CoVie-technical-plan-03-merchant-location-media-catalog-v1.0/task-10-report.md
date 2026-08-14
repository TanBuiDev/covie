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
