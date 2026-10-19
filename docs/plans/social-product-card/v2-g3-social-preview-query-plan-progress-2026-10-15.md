# Social Product Card 2.0 — G3 local query-plan evidence

This advances the unmeasured-query checkpoint in
`v2-g3-social-preview-backend-progress-2026-10-15.md`. It does not complete G3,
the deployment performance gate or the whole Social V2 plan. Flags stay OFF.

## Change and scope

Added one opt-in `SocialPreviewQueryPlanIntegrationTest`, using the repository's
existing disposable PostgreSQL/StatementInspector approach. No production
backend, index, migration, API/client or UI change was warranted by the result.
Previously verified contract, media and UI tests were not rerun.

The synthetic fixture contains 1,000 active merchants, 20,000 public items and
200,000 READY image assets/associations: ten images per item. Captions contain
3,000 ASCII characters. Setup is inside a rollback transaction against the
test container, never the recovered runtime database. Tables are analyzed after
insertion; planner scan preferences are not forced.

The test invokes the actual HTTP controller/security/service route through
MockMvc for batches of 1 and 40 distinct visible items. It captures the exact
named-JDBC statement and actual Hibernate media lookup SQL. For either size it
observes one named data query plus one Hibernate SELECT, rather than per-card
detail queries. It asserts six unique returned images per item, full caption
length, expected plan output row counts and no sequential scans of the three
large catalog/media tables. SQL EXPLAIN uses the captured statements and
equivalent same-set UUID/status/purpose parameters.

## Measured results

`.local/social-v2-g3-query-plans.log`: **1 test passed**, zero
failures/errors/skips; **BUILD SUCCESS, 32.238 s**.

| Batch | Named/Hibernate data queries | Returned media | Response bytes | Single MockMvc duration | Batch SQL execution | Asset SQL execution |
| --- | --- | --- | --- | --- | --- | --- |
| 1 item | 1 / 1 | 6 | 4,042 | 139.388 ms | 0.133 ms | 0.039 ms |
| 40 items | 1 / 1 | 240 | 162,055 | 68.664 ms | 1.773 ms | 0.460 ms |

These are individual sequential observations, not percentiles or an assertion
that a larger batch is inherently faster. Initialization/warm caches and the
in-process HTTP harness affect them. The response byte counts describe this
ASCII fixture, not a worst-case UTF-8 caption bound.

- The single-item batch uses catalog/merchant primary-key index scans. The
  40-item batch uses a catalog bitmap access path, a hash join over a sequential
  scan of the 1,000-row merchant table, and 40 lateral limits of six rows.
- Association access is bitmap-backed; asset lookups use the existing primary
  key. Each lateral branch examines the fixture's ten associations, so 400
  internal asset index lookups occur for the 40-item SQL. These are PostgreSQL
  plan loops inside one data statement, not 400 application round trips.
- The separate batched asset SELECT returns exactly 240 rows for the larger
  request using bitmap access. No duplicate gallery IDs occur.
- All four plans report zero shared-block reads and zero temporary-block
  reads/writes. The 40-item batch reports 1,845 shared hit blocks; the asset
  lookup reports 642. This is warm local evidence, not cold-storage performance.

Exact statements and EXPLAIN JSON artifacts are in
`.local/social-v2-g3-query-plans/`: `batch-1.sql/json`, `assets-1.sql/json`,
`batch-40.sql/json`, `assets-40.sql/json`, plus `measurements.txt`.

Reproduction from an isolated API copy:

```powershell
.\mvnw.cmd -o '-Dtest=SocialPreviewQueryPlanIntegrationTest' '-Dcovie.test.social-preview-plan=true' '-Dcovie.test.social-preview-plan-output=E:/WebApp/CoVie/.local/social-v2-g3-query-plans' test
```

Do not overlap Maven invocations or point the fixture at a runtime database.
The test is opt-in so routine backend runs do not repeatedly create this fixture.

## Conclusion and remaining work

The captured local plans support bounded application query count and existing
indexed large-table access at this fixture size. They provide no evidence that
a new index or a production query refactor is necessary. Existing BE is kept.

Representative staging data, cold caches, concurrent HTTP traffic, P95/error
rates and system saturation have not been measured. The local test does not
prove hide/read race handling, object-storage privacy or live invalidation.
No staging exists; no release gate is declared passed from these local timings.

Next implementation work is Social-only batch consumption and the shared
caption/gallery Card using the verified exact Figma context, while keeping
Catalog mode free of preview requests. Shared seller draft preview and remaining
G1/G2, G4–G6 requirements remain open. No original service restart, recovered-data
write, activation, commit or deployment occurred.
