# Social V2 — actual local read load, SQL plans and browser metrics

Date:2026-10-17. Whole G0–G6 objective ACTIVE. Previous turn supplied concrete invalid-video/Edit/read evidence. This checkpoint measures previously missing performance scope; it does **not** declare production SLO, whole frontend performance or final project acceptance.

## Method and authoritative inputs

Unchanged current API image `covie-api:social-v2-video-runtime-20261017`, ID `sha256:c3159a643cc38c10468d53ce5e2cdf150211f0312b21753a73d47edd1d6ad5cb`,1140source hashes checked before/after. Fresh disposable PG18/MinIO, generated RSA/password/login. Local profile, rate limiterOFF, technical retention/outbox dispatchOFF, isolated V2 flagsON. No production/recovered database or actual external credentials. Docker reports16logical CPUs and8207888384bytes available memory; not a controlled cloud instance capacity claim.

Controlled seeded dataset:40public/approved catalog items, each1440-ish-character Vietnamese caption and3READY image metadata associations (120total),800ACTIVE comments (20/item),80LIKE/SAVE rows. Exact preflight checks40preview entries/3media,40summary entries,5comments and20entries/nonemptycursor for each of LIKED/SAVED/COMMENTED. Media metadata is seeded for **API read load**, not actual upload/decoded image object proof. No storage object request occurs in those read-only API routes. The separate browser fixture below stores actual PNG objects.

Use the existing, unchanged `infra/perf/run-load.mjs`/`scenarios.mjs`. Six sequential scenarios, each10seconds/concurrency4, actual full response-body consumption. Existing provisional pilot limits p95≤400ms/error ratio<1% retained, not loosened. Real cookie from actual login for private activity; no fabricated JWT or mock response. First requests preflight/warm the endpoints; this is a short, warmed local baseline, **not** the full staging-duration runner, TLS/WAN/saturation/soak or approved production SLO.

## Actual API results

`.local/social-v2-local-load.log` exit0, fixture `.local/social-v2-video-chain-515f5273555b`, result completedTRUE. Per-scenario JSON/stdout, stderr, metric samples and SQL statistics retained there. Consolidation `.local/social-v2-local-load-summary.json`.

| Scenario | Requests / errors | p50 / p95 / p99 ms | Mean response bytes |
| --- | --- | --- | --- |
|40-ID previews |4443 /0 |8 /16 /25 |95801 |
|40-ID summary |17165 /0 |2 /4 /5 |5641 |
|comments limit5 |25895 /0 |1 /2 /4 |1143 |
|LIKED activity20 |8452 /0 |4 /8 /12 |6538 |
|SAVED activity20 |10749 /0 |4 /5 /6 |6538 |
|COMMENTED activity20 |9035 /0 |4 /6 /7 |8460 |

Total75739requests, zero failed/network-error responses; all six existing pilot checks exit0. Preview response95801bytes consumed4443times,425643843body bytes, not header-only timing.

120actual Actuator samples (20/scenario) observe Hikari maximum10, sampled active≤4 and pending0. Maximum sampled JVM **heap** used565471184bytes, not RSS or a leak/whole-memory bound. Sampled process.cpu.usage maximum0.5954545 is the reported gauge, not a production CPU budget. Final one-shot Docker snapshot API981.8MiB/PG245.5MiB; not peak RSS, and the fixture also extracted JAR/tool classes inside the API container. No claim that the API fits the earlier512MiB isolated probe-tool limit. Duration/sampling does not prove absence of long-term leaks or transient unsampled pool waits.

## Actual query counts, not an inferred N+1 claim

Fixture PG starts with pg_stat_statements, reset separately before each scenario. Real normalized queries/calls/rows/mean execution times saved as `<scenario>-sql.json`; no bound credentials or response bodies printed.

- Preview4443requests:4443main preview statements and4443batched media statements, each returning120rows/request. **Two application read statements per40-ID batch**, not one media lookup per item. Observed mean SQL1.770ms and0.165ms respectively.
- Summary17165requests:17165summary statements,40rows/request; mean0.505ms. Correlated counts remain inside the statement; this is a statement-count observation, not a claim of zero internal subplans.
- Comments25895requests:one visibility check and one bounded comment query each; the latter fetches6rows for the limit5/next-cursor contract.
- Each private activity request:one activity projection fetching21rows and one media batch fetching20rows, plus actual session read and conditional auth-session last_used_at UPDATE. Therefore this is **business-read load with expected session bookkeeping**, not a claim of zero database writes.
- Transaction commands and periodic operational-metric queries remain visible in raw statistics; not mislabeled as item-by-item lookups.

## Current-source SQL execution plans

Separate `.local/social-v2-current-query-plans.log` exit0, fixture `.local/social-v2-video-chain-b9db867f6c48`, completedTRUE; summary `.local/social-v2-current-query-plans-summary.json`. No API load scenario replay.

Extract actual text blocks from current `CatalogSocialPreviewService` and `MediaAssetRepository`; substitute fixture UUIDs/videoTRUE/cutoff timestamps only. Store the exact SQL and actual EXPLAIN(ANALYZE,BUFFERS,FORMAT JSON). These are literal-bound fixture plans, not every possible JDBC generic plan/production cardinality proof.

- Current40-ID/3-media preview returns120rows: planning1.072ms, execution1.591ms,339shared hits,0reads,0temporary writes.
- New cleanup claim measured after adding10000metadata-only rows:10120assets total,100eligible old unattached pending video,120existing links. Actual LIMIT/FORUPDATE/SKIPLOCKED plan returns1row: planning0.546ms, execution1.370ms,205shared hits,0reads/temporary writes. Plan scans10120assets, filters10020, hash anti-joins references, sorts candidates and locks the selected row. This is **claim SQL**, not actual object deletion/retention scheduling or a new race proof.

No index/optimizer/application change: measured samples supply no failing budget or evidenced performance defect justifying speculative optimization. They do not establish scalability to arbitrary millions of objects.

## Actual browser20→40-card measurements

`.local/social-v2-browser-load.log` exit0, fixture `.local/social-v2-video-chain-03de2a264266/{result.json,browser-metrics.json,measured-40-cards.png}`, completedTRUE. Summary `.local/social-v2-browser-load-summary.json`. Uses actual current copied Next16.4 **dev** server, warmed route, fresh measured Chrome155.0.8059.84 context1280×1000, real API/PG and120actual PNG objects stored by packaged S3 SDK. Those READY rows/objects are fixture setup, not another upload-flow acceptance claim. Search vectors use the current source rebuild expression for the40seeded products/offers.

Actual `/search?q=Local%20measured%20item&sort=NEWEST&view=social`:20cards, native LoadMore→40cards append in place, no writes/pageErrors/overflow. Six protected commerce tables full pre/post JSONB unchanged. Screenshot opened/visually reviewed. All measured responses originate from real servers; localhost3000 HTTP/WebSocket bridge forwards rather than invents payloads.

| Observation |20cards |40cards after append |
| --- | --- | --- |
|CDP JSHeapUsedSize bytes |24837600 |31527472 |
|DOM Nodes |2248 |4790 |
|CDP cumulative TaskDuration seconds |0.939674 |1.191945 |
|Observed long tasks |2:79ms,241ms |4total, additionally62ms,92ms |
|Decoded native PNGs |6 |16 |
|CLS without recent-input shifts |0.4269628784 |0.4269628784 |

Observed initial-navigation LCP entries2352ms then3796ms; LCP stops at first user input, not an independent “40-card LCP”. This run records actual observer entries/CDP metrics, not Lighthouse or production Core Web Vitals acceptance. One append is not a memory-leak/retention test.

27forwarded HTTP responses carry18797296**decoded body bytes**, including large development bundles; not compressed/on-wire or WAN bandwidth.16actual PNG responses200×103bytes=1648object-body bytes, intentionally lazy-visible media, not all120objects fetched. Recorded preview batches20,20,40, each200/within40-ID cap. The repeated20batch may be dev lifecycle behavior; not automatically labeled a BE duplicate-request bug.

### Finding requiring follow-up, not a green frontend gate

**Measured dev CLS0.427 is material and remains unresolved as a production-risk hypothesis.** Captions arrive asynchronously after discovery cards render; source makes a late content-height change plausible. Dev compilation/hydration/network interception can affect timing, so do not infer a production regression or rewrite working UI from this metric alone. Build a separate isolated current-source feature-ON production-mode verification artifact, reproduce/attribute the shift there, then change only an evidenced root cause if confirmed. Existing normal feature-OFF production image/source stays untouched. Do not report the browser harness's exit0 (measurement assertions) as passing a CLS/LCP quality threshold.

## Preservation and remaining scope

No application/API/client/schema/migration/CSS/dependency change, full suite, unchanged earlier functional rerun or normal-source build. All owned API/PG/MinIO/Next containers/processes/networks and generated env/PEM/auth manifests removed; normal3000/8081 untouched/absent. Current1140API/598web source hashes remain the existing image inputs. No existing/recovered data, remote Git/commit/branch/merge, activation, registry push or deploy.

Next action: Verify and attribute the measured late layout shift using an isolated feature-ON production-mode build, preserving the normal OFF artifact; fix only if actual evidence confirms a product defect, then continue whole-plan acceptance/rollback audit.
Required from you: Nothing
Completion condition: Production-mode evidence resolves the CLS hypothesis and any confirmed defect, without upgrading local pilot metrics to staging/production SLO claims.
