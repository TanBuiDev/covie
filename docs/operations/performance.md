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

Available scenarios are defined in `infra/perf/scenarios.mjs`. Authenticated Order-list load uses `COVIE_LOAD_AUTH_COOKIE`; item detail uses `COVIE_LOAD_ITEM_PATH`. The harness prints only aggregate status/latency data and never response bodies.

Pilot objectives currently encoded:

- public catalog search: concurrency 50, p95 <= 500 ms, errors < 1%;
- public item detail: concurrency 50, p95 <= 300 ms, errors < 1%;
- authenticated Order list: concurrency 20, p95 <= 500 ms, errors < 1%.

During staging load also record CPU/memory, Hikari active/max, PostgreSQL connections, outbox oldest-due age, Redis errors and rate-limit fail-open metrics. Sustained Hikari active/max should remain below 80% and outbox oldest due should settle below 60 seconds.

## Concurrency correctness

HTTP latency is not proof of transactional correctness. The release gate must rerun the existing PostgreSQL-backed races for last-stock, reservation, idempotency and Order command boundaries at soak scale. On the current Plan 10 branch, Plan 09's final browser/concurrency fixture layer is not present, so those environment-backed soak executions remain a staging prerequisite rather than being faked with in-memory tests.

Required staging evidence includes 100 iterations of stock=1/two buyers with exactly one successful Order and no oversell, independent parallel checkouts without shared-lock bottlenecks, and repeated supported Order-command races without duplicate events/inventory movements.
