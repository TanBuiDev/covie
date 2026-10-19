# G1 Admin list/detail: actual query evidence and scoped offer index

## Measured scope

`AdminCatalogReadQueryPlanIntegrationTest` is an opt-in, single targeted test
using an actual issued Admin JWT through MockMvc and disposable PostgreSQL 18.
Its analyzed synthetic fixture contains 1,000 merchants, 20,000 items with distinct
timestamps, 200,000 READY image assets/gallery links, 20,000 variants, 3,000
locations and 60,000 offers (three per item). Captions are 3,000 ASCII characters.
It observes default size 20, maximum size 100, one combined merchant/type/listing/
moderation/search request with CREATED_ASC ordering, and one detail with ten
images/three locations. It captures the named SQL actually executed and the
Hibernate media SELECT, then runs unforced EXPLAIN ANALYZE BUFFERS against those
statements with their parameters.

This is not a repeated history-index fixture, full API suite, production traffic,
concurrent load, cold-cache test or P95 measurement. The detail fixture has no
moderation cases; its case queries are not new evidence for heavy history.

## What passed unchanged

- List size 20 and 100 both execute two named statements plus one bounded asset
  SELECT. The filtered request has the same count. No per-item application-level
  media query appeared. Authentication queries are outside these stated counts.
- Cover subplans execute only for returned rows: 20/100 outer loops, ten media
  links per item, existing gallery/media PK indexes; not all 20,000 items.
- The unfiltered list count/main query intentionally scan the 20,000-item dataset
  for its total/global ordering. The main statement uses top-N heapsort, with no
  temporary I/O, and measured 13.880/16.429 ms after the change below. This is not
  sufficient evidence to change search, total semantics or add sorting indexes.
- Combined merchant/search uses existing `ix_catalog_items_merchant_state`, with
  count 0.094 ms, main query 0.789 ms. The test does not isolate unrestricted text
  search or every allowed sort/filter combination; those are not claimed passed.
- Detail has six named statements plus one ten-ID asset SELECT. Content/media
  statements remain unchanged. Asset measurements are 0.099–0.236 ms across the
  four requests; single HTTP measurements are 35.356–263.281 ms, not a load metric.

## Concrete risk, RED and minimal correction

The original detail location lookup returned three locations but scanned all
60,000 `location_variants` rows, touching 1,047 shared blocks overall (1,035 for
the offer heap), measured 6.516 ms. Existing indexes lead with location ID and
are partial on nondeleted rows; this Admin query starts from a variant and
includes historical/deleted offers. This evidenced an unindexed one-item lookup,
not a reason to rewrite the rest of the service.

The new targeted assertion fails on that exact Seq Scan:
`.local/social-v2-g1-read-plans-red.log`, one expected failure. Baseline artifacts
are `.local/social-v2-g1-read-plans/`; RED artifacts have the `-red` suffix.

V022 adds only `ix_location_variant_variant` on `location_variants(variant_id)`.
It is not partial, preserving the existing Admin lookup scope. There is no
business row, query, DTO, API or generated-client change. After migration, the
unforced plan uses Bitmap Index/Heap scans for three offers: 17 total shared
blocks (five offer heap blocks), measured 0.063 ms. No temporary I/O was recorded
in the captured plans. Timing is warm/local and not a production speed guarantee.

## Migration and deployment compatibility

V022 uses CREATE INDEX CONCURRENTLY, with its `.sql.conf` setting
`executeInTransaction=false`. Application configuration uses session-level
Flyway advisory locks (`spring.flyway.postgresql.transactional-lock=false`), not
an unlocked migration. This follows the documented
[Flyway concurrent-index lock requirement](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-postgresql-namespace/flyway-postgresql-transactional-lock-setting).
The three standalone test migration runners that can apply latest migrations
were updated with the equivalent Flyway setting; earlier targeted migrations
retain their existing configuration. The backup/restore runner's setting change
was compiled but its end-to-end backup script sequence was not rerun.

The populated upgrade test retains its explicit 019→020 and 020→021 checkpoints,
then applies exactly one 021→022 migration. All business-table canonical JSONB
snapshots remain unchanged, the offer table heap identity is unchanged, the index
is ready/valid and nonpartial, Flyway validates 22 migrations and reapplication
executes zero. This small upgrade fixture is not a large-table build/lock test.

Final verification: **three tests PASS, 59.453 seconds**, exit 0, in
`.local/social-v2-g1-read-plans-green.log`: read-plan test 28.45 s, populated
migration 12.77 s, standalone outbox metrics 4.535 s. The latter also exercises
fresh latest migration with the explicit standalone lock configuration. Exact
plans/SQL/measurements: `.local/social-v2-g1-read-plans-green/`.

`docs/operations/deploy-rollback.md` records standalone Flyway configuration,
validity checks, staging build/disk/long-transaction gates and nontransactional
failure recovery precautions. No live AWS, recovered/runtime DB, normal process
restart, remote repository, normal feature flag or deployment was touched.
Normal web/API PIDs remain unchanged. Only disposable test schemas received V022.

## Remaining completion audit

G1 is not complete: actual Admin browser flows, privacy/concurrency/load and
staging evidence remain separate gates. The approved G1.1 summary also names
caption/video presence flags; the current summary DTO has neither. Detail does
return the actual caption. That response gap needs explicit plan/source
reconciliation, not a false claim that query measurements finish G1 or an
invented video implementation before G5. No staging exists; normal V2/video flags
remain OFF and the entire objective remains active.

Next action: Reconcile approved Admin summary caption/video metadata with the current DTO and phase dependencies.
Required from you: Nothing
Completion condition: Identify the minimal contract work still required without fabricating video capability or repeating achieved query gates.
