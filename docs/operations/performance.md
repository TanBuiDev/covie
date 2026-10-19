# Pilot performance and load gates

Performance gates use a production-like PostgreSQL database and a staging deployment. Tiny local databases are not accepted as evidence.

## Query-plan gate

`HotQueryPlanIntegrationTest` is tagged `performance` and is skipped unless `COVIE_PERF_JDBC_URL` and `COVIE_PERF_QUERY_FILE` are supplied. The target must have Flyway V013 or a reviewed later migration and must meet the minimum pilot-scale row counts in `PerformanceFixtureBuilder`.

The query file must contain the **exact repository SQL** statements separated by lines such as:

```text
-- COVIE-HOT-QUERY: catalog-discovery
```

The test prepends `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` and rejects OFFSET-style hot keyset queries and broad sequential scans on the most sensitive historical tables. The purpose is to catch planner regressions, not to assert exact planner cost or milliseconds.

Run:

```bash
COVIE_PERF_JDBC_URL='jdbc:postgresql://...' \
COVIE_PERF_DB_USER='...' \
COVIE_PERF_DB_PASSWORD='...' \
COVIE_PERF_QUERY_FILE='/absolute/path/hot-queries.sql' \
./infra/perf/run-query-plan-checks.sh
```

If evidence proves a missing index, preserve before/after EXPLAIN output and review a new Flyway migration. Do not edit V001–V013 merely to make the test green.

## HTTP load harness

Node 24's native `fetch` is used; no load-test package is required.

```bash
node infra/perf/run-load.mjs \
  --base-url https://staging.covie.vn \
  --scenario public-search \
  --concurrency 50 \
  --duration 120s
```

Available scenarios are defined in `infra/perf/scenarios.mjs`. Authenticated Order-list and Social activity load use `COVIE_LOAD_AUTH_COOKIE`; item detail uses `COVIE_LOAD_ITEM_PATH`. The harness prints aggregate status/latency/body-size data, never cookies or response bodies. Supply disposable credentials through the approved local configuration, not command literals, chat or shared logs; send them only to the intended trusted staging origin.

Latency now includes reading the complete response body, not only receiving headers. Truncated/timed-out bodies count as failed transfers even when headers said HTTP200. `responseBodyBytes` reports total/min/max/rounded mean bytes and completed-response count for fully consumed bodies, including non-200 responses. Partial failed bodies are not included in those size totals. Bytes are the decoded chunks exposed by Fetch, not compressed wire bytes or an estimate of object-storage/CDN transfer. Streaming consumption does not retain response bodies. Earlier header-only measurements are not directly comparable; collect a new baseline.

### Social V2 read preparation

- `public-social-previews`: repeated `itemIds` from `COVIE_SOCIAL_LOAD_ITEM_IDS`,1–40 distinct valid UUIDs; no auth cookie.
- `private-social-activity-liked`, `private-social-activity-saved`, `private-social-activity-commented`: actual `/api/v1/me/social/activity` types with limit20, required `COVIE_LOAD_AUTH_COOKIE`, no client userId.

These scenarios reuse the provisional Phase4 social-read objectives p95<=400ms and errors<1%, **not an approved V2 production SLO**. Keep flags OFF in normal environments. An authorized staging candidate must explicitly enable the relevant backend features before these reads can succeed; the harness never changes flags or creates data. A401/404 is not an accepted sample. This preparation does not extend the old Phase4 runner's two-scenario scope into full V2 acceptance.

Before V2 load, independently verify the exact candidate/active deployment manifests and fixture visibility. Seed20–40 actual caption/media items for preview, and enough actual current Like/Save/ACTIVE comments for each activity type (including multiple comments/item). Check the returned representative IDs/counts and payload before interpreting performance; an empty200 response is not proof the rich fixture was exercised. Use disposable staging identities. Do not replace missing staging data with response mocks.

```bash
node --test infra/perf/social-scenarios.test.mjs infra/perf/run-load.test.mjs
node infra/perf/run-load.mjs \
  --base-url https://staging.covie.vn \
  --scenario public-social-previews --concurrency 10 --duration 30s
```

Run each authenticated activity scenario separately with the configured disposable cookie; record which type, fixture and actor role were exercised without recording the cookie. The unit/loopback tests validate contract and measurement behavior only; they are not staging load results. DB pool/query count, browser network/long tasks/LCP/CLS/media/memory, load baseline and owner-approved SLO still require separate real-environment evidence under Social V2 plan11.5/12.4.

Pilot objectives currently encoded:

- public catalog search: concurrency 50, p95 <= 500 ms, errors < 1%;
- public item detail: concurrency 50, p95 <= 300 ms, errors < 1%;
- authenticated Order list: concurrency 20, p95 <= 500 ms, errors < 1%.

During staging load also record CPU/memory, Hikari active/max, PostgreSQL connections, outbox oldest-due age, Redis errors and rate-limit fail-open metrics. Sustained Hikari active/max should remain below 80% and outbox oldest due should settle below 60 seconds.

## Concurrency correctness

HTTP latency is not proof of transactional correctness. The release gate must rerun the existing PostgreSQL-backed races for last-stock, reservation, idempotency and Order command boundaries at soak scale. On the current Plan 10 branch, Plan 09's final browser/concurrency fixture layer is not present, so those environment-backed soak executions remain a staging prerequisite rather than being faked with in-memory tests.

Required staging evidence includes 100 iterations of stock=1/two buyers with exactly one successful Order and no oversell, independent parallel checkouts without shared-lock bottlenecks, and repeated supported Order-command races without duplicate events/inventory movements.

## Disposable bulk fixture preparation

`PilotFixtureIntegrationTest` loads the deterministic SQL resource `performance/pilot-fixture.sql` through bulk JDBC into the Testcontainers PostgreSQL/PostGIS test database. It is opt-in (`covie.test.pilot-fixture=true`), tagged performance, and runs inside a rollback transaction with scheduled outbox/retention disabled. Do not use the resource as a project database seed.

```powershell
cd apps/api
.\mvnw.cmd -o '-Dcovie.test.pilot-fixture=true' '-Dtest=PilotFixtureIntegrationTest,PerformanceFixtureBuilderTest' test
```

The fixture supplies 100 areas with valid polygons, 1,000 merchants, 2,000 distinct location points, 20,000 catalog items, 30,000 variants, 40,000 correctly owned location offers, 10,000 Needs, 50,100 Orders, 200,100 Order events, 100,000 notifications and 50,000 outbox records (100 pending/failed). Active/paused, active/expired and read/unread states are mixed. Area-point association is checked with a one-metre tolerance at polygon boundaries. Pending Orders have only their creation event; completed Order event sequences are synthetic preparation, not normal-auth lifecycle acceptance.

This fixture verification proves minimum scale and selected data invariants. It does not run the eight required hot-query families or HTTP load gate, and does not turn the existing opt-in HotQueryPlanIntegrationTest skip into a performance pass. Capture actual repository SQL/bound parameters and EXPLAIN plans against this dataset next; staging latency/CPU/pool observations remain separate.
2026-10-11 pilot fixture follow-up: actual Outbox due and notification first/cursor repository SQL/bindings/JSON plans pass on the full fixture with default planner; artifacts in .local/perf-pilot-20261011. Six other required query families and staging load/runtime remain open.

2026-10-11 follow-up: seller Order location first/cursor, confirmation-expiry scheduler and active-cart user repository plans pass with default planner; fixture adds 10,000 carts (1,000 active). Actual SQL/bindings/plans are under .local/perf-pilot-20261011. Other seller/scheduler branches, catalog/coverage/Needs and staging load remain open.

2026-10-11 JDBC discovery follow-up: actual catalog query/category/location SQL uses the location GIST index; Seller Need first-page SQL returns 31 eligible matches and uses the location-offer access path. Exact named SQL/typed fixture parameters/EXPLAIN are saved in .local/perf-pilot-20261011. Coverage and other discovery/scheduler branches plus staging load remain open.

## Pilot Product/Service auto-completion query evidence — 2026-10-11
Extended disposable rollback fixture with 25,000 Product and 25,000 Service fulfillments. Each family has 100 due awaiting-confirmation orders, 100 future awaiting-confirmation orders, and completed historical orders retaining past auto_complete_at. Service order type/shipping invariants match schema. Actual OrderRepository queries return exactly the expected 100 IDs, excluding future/completed cohorts. Captured Hibernate SQL with original status/now/limit100 bindings and EXPLAIN(default planner) stored in .local/perf-pilot-20261011/orders-{product,service}-auto-completion files.
Initial 5-test run failed a too-specific expectation of fulfillment time index, not business result: PostgreSQL chose ix_orders_admin_state_created plus hash join/fulfillment sequential scan because almost all historical timestamps are past. No evidence warrants changing production SQL/index or forcing planner. Assertion now permits the existing order-state index or corresponding fulfillment-time index, retains existing healthy-plan/no-OFFSET checks and exact candidate set. Actual plans honestly include Seq Scan over 25,000 fulfillments (24,900 due timestamps) and state index over 400 awaiting orders; observed execution 3.679ms Product/3.690ms Service is synthetic evidence, not staging latency acceptance. No new blanket ban on fulfillment scans or release claim.
Final PilotFixtureIntegrationTest + HotQueryPlanValidationTest: 5 tests, zero failures/errors/skips, BUILD SUCCESS 34.402s, terminal14:08:21+07, .local/backend-pilot-auto-completion-green-20261011.log, session88139 exit0. Initial .local/backend-pilot-auto-completion-20261011.log session50037 exit1. Production BE/schema/index unchanged this turn; no test writes to recovered DB. Full793 gate predates these test-only additions. Whole goal remains ACTIVE: multi-location Seller Order branches, Service/hybrid coverage and other catalog sorts, broader contract/provider/runtime and staging load remain open. Query eligibility/plan proof is not scheduler transition/race proof.

## Pilot multi-location Seller Order pagination — 2026-10-11
Added actual repository traversal on existing 50,100-order pilot fixture. Merchant1 owns51orders;25 moved to its secondarylocation1001 in rollback-only test, Product pickup references updated consistently, EntityManager flush/clear prevents stale fixture entities. All-location first/cursor pages21/21/9/empty match exact DB createdAtDESC/idDESC order, no duplicates/omissions, both locations present. Each location separately matches exact expected list and no cross-location results. Hibernate SQL/bindings/default EXPLAIN for all-location first/cursor saved in .local/perf-pilot-20261011; existing ix_orders_merchant_created_v010 used. Other merchant IDs excluded by exact51expected set against50,100rows.
Initial test failed from JDBC fixture mutation bypassing already-loaded JPA entities (test setup only); .local/backend-pilot-multi-location-orders-20261011.log session7013exit1. Corrected fixture fresh PilotFixtureIntegrationTest+HotQueryPlanValidationTest5tests0fail/errors/skips BUILD SUCCESS34.497s terminal14:10:53+07, .local/backend-pilot-multi-location-orders-green-20261011.log session53089exit0. Production BE unchanged; no recoveredDB test writes. Full793 predates test-only addition; no unnecessary full rerun. WholeBE ACTIVE; next Service coverage/hybrid policy and broader contract/runtime/provider/staging gates. This is repository data/plan evidence, not HTTP ownership/race/load acceptance.

## Pilot Service coverage — 2026-10-11
Actual JdbcCatalogDiscoveryRepository executed against full pilot fixture in disposable rollback transaction. Test-only projection converts catalog/category to SERVICE, locations service_enabled=true, coverage_type=SERVICE_AREA after Product checks. Radius-only context returns nonempty rows all distance<=300m. Matching area returns rows beyond300m. Hybrid radius+area returns nearby rows without area context and beyond-radius rows with matching area (OR semantics). All coverages INACTIVE excludes all service matches; restoring ACTIVE then service_enabled=false also excludes all. Actual SQL/typedbindings/defaultplanner EXPLAIN for service radius/area/hybrid saved under .local/perf-pilot-20261011 and location GIST access path confirmed. Fresh PilotFixtureIntegrationTest+HotQueryPlanValidationTest5tests0fail/errors/skips BUILD SUCCESS35.938s terminal14:12:28+07 .local/backend-pilot-service-coverage-20261011.log session1601exit0. ProductionBE unchanged/no recovered test writes. This is catalog canServe query evidence, not full service checkout/coverage configuration HTTP proof or staging load. Full793 predates test-only additions. WholeBE ACTIVE; next other catalog sorts/cursor/filter branches and remaining broader contract/runtime/provider/staging requirements.

## Pilot five catalog sorts + encoded cursors — 2026-10-11
Actual JdbcCatalogDiscoveryRepository tested all RELEVANCE/DISTANCE/PRICE_ASC/PRICE_DESC/NEWEST sorts against full pilot dataset, query rice, PRODUCT, no category, pickup,1km, inclusive price10200..10400. Nonempty full baseline independently checked by Java sort comparators (UUID string lexical order matches PostgreSQL UUID); all results price bounded. Paged traversal exposes20of21lookahead rows, actual sort-specific CatalogCursorCodec roundtrip, exact baseline ID sequence/no duplicates/omissions. Actual first+cursor SQL/typedbindings/defaultEXPLAIN for each sort saved catalog-<sort>-filtered-{0,1}, location GIST access confirmed. Initial test input price12000..16000/category1 had no nearby matching fixtures; corrected test input only, no BE bug. Final PilotFixtureIntegrationTest+HotQueryPlanValidationTest5tests0fail/errors/skips BUILD SUCCESS37.507s terminal14:15:15+07 .local/backend-pilot-catalog-sorts-green-20261011.log session99339exit0. Initial session79529exit1. Production unchanged/no recovered test writes. WholeBE ACTIVE; remaining no-query relevance fallback, shipping/openNow/listing-kind/stock and other HTTP contracts/runtime/provider/staging evidence to assess. Full793 predates test-only expansions; current gate is synthetic repository evidence, not HTTP load or whole release acceptance.
