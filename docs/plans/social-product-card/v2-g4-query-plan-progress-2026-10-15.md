# Social Product Card 2.0 — G4 local query-count/plan evidence

## Scope

Added one opt-in `SocialActivityQueryPlanIntegrationTest`. Existing production
queries and V018 indexes passed without modification. No backend optimization,
new index or migration was warranted by the measured result. Earlier unchanged
visibility/cursor/contract/UI checks were not rerun.

The fixture uses disposable PostgreSQL inside a rollback test transaction:

- 1,000 active users, 1,000 active merchants, 20,000 public items.
- 20,000 READY CATALOG_IMAGE covers and associations, one per item.
- 200,000 reaction rows and 200,000 ACTIVE comments.
- Per actor: 100 LIKE, 100 SAVE and 200 comment rows; comment bodies contain
  1,000 ASCII characters. Activity timestamps vary by seconds within each actor.

Tables are analyzed after insertion, with no forced index/scan planner settings.
The test issues a real account JWT through the existing issuer and invokes the
actual secured HTTP route via MockMvc, not only the service or a copied query.

For LIKED, SAVED and COMMENTED it observes the default-20 first page, explicit-50
first page and its 50-row cursor page: nine sequential observations in one test.
All have real thumbnail resolution and the expected number of returned items.

## Exact query evidence

Each observation captures **one named activity data query and one Hibernate
batch asset SELECT**. That is scoped to activity data access, not a claim that
the entire authenticated request performs only two SQL statements: authentication
and session validation remain separate infrastructure.

EXPLAIN ANALYZE/BUFFERS uses the captured SQL and equivalent same-set UUID and
status/purpose parameters. SQL plus JSON plans are saved for every data/asset
query in `.local/social-v2-g4-query-plans/`; `measurements.txt` records request
durations, byte counts and query counts. The test asserts bounded output rows
and no sequential scans of the five large activity/catalog/media tables.

Observed existing access paths:

- Reaction first pages use `ix_catalog_social_reactions_user_kind`; cursor pages
  use a bitmap path through that same index.
- Comment first/cursor pages use `ix_catalog_social_comments_author_recent`.
- Catalog, merchant, association and asset joins use their existing primary keys.
- Asset materialization uses a bitmap access path, at most 50 IDs per page.
- Data results are 21/51 rows for lookahead, except exhausted 50-row reaction
  cursor pages. Internal incremental-sort/join loops can read one extra row;
  they are plan operations, not extra application round trips.
- All eighteen plans report zero shared-block reads and zero temporary-block
  reads/writes in this warm local execution.

## Measurements

`.local/social-v2-g4-query-plans.log`: **1 test passes, 36.209 s**, zero
failures/errors/skips.

| Request | Data SQL execution | Single MockMvc duration | Response bytes |
| --- | --- | --- | --- |
| LIKED default 20 | 0.327 ms | 176.649 ms | 5,069 |
| LIKED 50 first | 0.517 ms | 27.293 ms | 12,362 |
| LIKED 50 cursor | 0.519 ms | 56.330 ms | 12,311 |
| SAVED default 20 | 0.349 ms | 19.606 ms | 5,066 |
| SAVED 50 first | 0.525 ms | 20.983 ms | 12,356 |
| SAVED 50 cursor | 0.558 ms | 19.982 ms | 12,311 |
| COMMENTED default 20 | 0.274 ms | 18.079 ms | 26,479 |
| COMMENTED 50 first | 0.526 ms | 19.141 ms | 65,899 |
| COMMENTED 50 cursor | 0.530 ms | 19.641 ms | 65,929 |

Separate asset SQL executions range from 0.057 to 0.127 ms. Single in-process
request durations include initialization/security/serialization and are **not**
P95s, throughput, network or production latency. ASCII payload sizes do not
establish worst-case UTF-8 bounds.

Reproduce only in an isolated API test copy, without overlapping Maven:

```powershell
.\mvnw.cmd -o '-Dtest=SocialActivityQueryPlanIntegrationTest' '-Dcovie.test.social-activity-plan=true' '-Dcovie.test.social-activity-plan-output=E:/WebApp/CoVie/.local/social-v2-g4-query-plans' test
```

The opt-in gate prevents creating this volume in every ordinary BE test run.
No runtime service, recovered database, flags or original process was changed.

## Conclusion and limits

At this fixture distribution, current queries have bounded application data
round trips and existing indexed access. Keep the working production code and
indexes; no speculative optimization was made.

This is synthetic, warm, all-public data with at most 200 activity rows per actor.
It does not prove cold-cache behavior, accounts with very large histories,
long runs of invisible/deleted entries, concurrent traffic or staging saturation.
Functional timestamp ties and restricted-state filtering have separate evidence;
they are not load measurements. No full release performance gate is declared.

G4 activity UI/profile entry, rendered authorization/account switching and
append/retry behavior still need implementation/verification. Remaining G1–G3
browser/concurrency/storage gates, G5 video and G6 staging/release evidence stay
open. No staging exists. No full suite, unchanged client generation/typecheck,
activation, commit or deployment was performed. All flags stay OFF and the full
objective remains active.
