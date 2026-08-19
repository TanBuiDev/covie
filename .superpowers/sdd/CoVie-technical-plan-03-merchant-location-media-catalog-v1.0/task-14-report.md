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

## Review round 1 hardening

- Spring MVC type-conversion failures for all typed discovery inputs now return `CATALOG_FILTER_INVALID` with HTTP 422 rather than a server error.
- Cursor decoding/encoding now rejects negative rank, distance, or price values, plus fields not applicable to the requested sort. The database binding for `NEWEST` uses PostgreSQL `TIMESTAMP WITH TIME ZONE` explicitly.
- The PostgreSQL/PostGIS test matrix now covers PRODUCT and SERVICE entries, the product-only RESALE rule, accent-insensitive FTS, paused item/variant/unavailable-offer exclusion, pickup/category/min/max filters, five sort keysets with tied IDs, and weekly hours plus closed/open exception-day `openNow` behaviour.
- Controller integration coverage now proves exact public summary IDs, opaque cursor continuation, a saved address owned by the authenticated user, and `RESOURCE_NOT_FOUND` for a foreign saved address.

## Verification after review

```text
mvnw.cmd -Dtest=CatalogCursorCodecTest,CatalogDiscoveryRepositoryIntegrationTest,PublicCatalogDiscoveryControllerIntegrationTest test
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

```text
mvnw.cmd test
80 test reports, no failures or errors
```

## Review round 2 test evidence

- Split category, pickup, type, and resale assertions into independent fixtures: a wrong-category peer, a non-pickup peer beside a pickup peer, product and service peers with materially different prices, and a valid product resale fixture beside a same-category service fixture.
- Replaced the one-step cursor assertion with full traversal at a page size of one for every sort. The test proves all four tied IDs have the exact expected ascending or descending UUID order and appear once only, catching both duplicate and skipped ties.

## Verification after round 2

```text
mvnw.cmd -Dtest=CatalogCursorCodecTest,CatalogDiscoveryRepositoryIntegrationTest,PublicCatalogDiscoveryControllerIntegrationTest test
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
```
