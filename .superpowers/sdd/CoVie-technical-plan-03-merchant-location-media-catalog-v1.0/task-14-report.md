# Task 14 Report: Public Catalog Discovery

## Delivered

- Added `GET /api/v1/catalog/items` with an explicit coordinate or owned-address location context.
- Added normalized catalog discovery filters, bounded radius/price/page settings, whitelist-only sorting, and 422 catalog-filter errors.
- Added a PostgreSQL/PostGIS JDBC CTE that chooses an item's nearest eligible active merchant location before calculating the active offer price at that location.
- Added database-side FTS relevance, pickup/open-now filtering, keyset pagination with `LIMIT + 1`, and no public `COUNT` query.
- Added an opaque Base64URL JSON cursor. Its sort and normalized discovery context are checked on decode; all decoded values remain JDBC bind parameters.
- Added compact item summaries with merchant, resolved location, primary ready media, and resolved-location `priceFrom`.

## Verification

```text
mvnw.cmd -Dtest=CatalogCursorCodecTest,CatalogDiscoveryRepositoryIntegrationTest,PublicCatalogDiscoveryControllerIntegrationTest test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

The PostGIS integration fixture proves that a nearer offer is retained even when a farther location is cheaper, radius filtering excludes an outside-only item, relevance cursor progression has no duplicate item, and inactive merchant/location records are hidden. Controller tests verify the required location context and 422 invalid-filter behavior.
