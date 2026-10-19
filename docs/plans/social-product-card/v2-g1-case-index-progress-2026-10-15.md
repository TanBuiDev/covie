# Social Product Card 2.0 — G1 item-scoped moderation history query plans

## Evidence before change

Admin case-history correctness had already passed; it was not rewritten. The
missing query-plan gate was measured using one opt-in integration test with real
issued Admin JWT, MockMvc, a disposable PostgreSQL container, 20,000 catalog items
and 200,050 moderation cases. Selected item has 60 cases; other items account for
the remaining cases. First/default page returns 50, cursor page returns 10, with
60 unique IDs and the same total. Fixture data is rolled back/discarded.

Captured the two actual NamedParameterJdbcTemplate moderation queries per
request: item-scoped total and page data. This excludes the item-existence lookup,
authentication/session SQL and other non-moderation work; it is not a claim of
two total HTTP-request queries. EXPLAIN ANALYZE/BUFFERS ran those exact captured
SQL strings and parameters without planner settings or forced indexes.

Baseline artifacts: `.local/social-v2-g1-case-query-plans-baseline/` and matching
`.log`. All four moderation plans use parallel sequential scans of the entire
moderation table to obtain one item's cases. Each touches 3,078 heap blocks;
data plans also sort. Warm SQL execution is 14.868–15.613 ms per query. This
demonstrates avoidable work proportional to unrelated cases on each continuation.
Existing V013 indexes do not index general item history: its item-specific unique
index applies only to OPEN PRE_APPROVAL cases.

The regression assertion rejects a moderation-table Seq Scan at this fixture
size. RED: `.local/social-v2-g1-case-query-plans-red.log`, one failure on Seq Scan.
Two earlier harness attempts counted Mockito's internal query delegation and then
included zero-argument accessor invocations. Those test-only collection errors
were corrected before measuring the baseline; they were not product defects.

## Minimal correction and preservation

Added V021, one partial non-unique B-tree index:
`(target_catalog_item_id, opened_at DESC, id DESC)` where item ID is not null.
No query, response, business rule, case mutation, existing index or earlier
migration changed. The index supports the existing item predicate and cursor
order without including non-catalog targets.

The populated upgrade test retains the explicit V019→V020 caption phase, then
checks V020→V021 separately. Existing catalog and user-targeted cases are included
in its canonical all-business-table snapshots. It verifies index definition,
Flyway validation/current version 021, no reapplication, unchanged business rows
and unchanged catalog heap identity. Existing user/address/order/fulfillment and
caption preservation checks remain in place.

## Verification

`.local/social-v2-g1-case-index-green.log`: **2 tests pass, 41.012 s**, zero
failures/errors/skips: query-plan regression and populated migration upgrade.
Final plans/measurements: `.local/social-v2-g1-case-query-plans-green/`.

All four plans naturally use Bitmap Index Scan on
`ix_moderation_cases_catalog_recent` plus Bitmap Heap Scan. Selected heap visits
are 60 rows for counts/first page and 10 for the cursor page; heap hit blocks are
14–16. Warm SQL execution is **0.058–0.099 ms** per moderation query. Sorts for the
small selected set can remain; no query rewrite was needed. These are local
synthetic warm observations, not staging load, cold-cache results or a P95 SLA.
Single HTTP timings include authentication overhead and are not a load benchmark.

The index is transactional CREATE INDEX, not a zero-downtime deployment proof.
Production-size build time, write blocking, disk budget and migration scheduling
still need staging/operational evidence before application to a live database.
No recovered/runtime database was queried or migrated. No service was restarted,
flag enabled, API contract regenerated, broad BE suite rerun, commit or deployment
performed. The existing development service was left running untouched; only
isolated test builds ran sequentially. G1 list/detail query plans and remaining
browser/privacy/concurrency gates are not proved by this case-history slice.
