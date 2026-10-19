# Social V2 operational signals

This maps Social Product Card plan12.3 to actual application metrics and operator
measurements. It does not declare a production SLO, activate features, install
monitoring or prove a deployed scrape/notification path. Keep normal V2/video
flags OFF until their activation gates pass. Management9091 remains private.

## Provisioned dashboard

The existing file provider loads `covie-social-v2.json` alongside the pilot
overview. Expressions use Spring's route templates, never actual item/media/user
IDs. Production already enables `http.server.requests` histograms. Missing HTTP
series after restart/no traffic are **no data**, not successful requests; no
`or vector(0)` masks missing collection. Confirm actual URI labels after deploy
before interpreting route panels, particularly requests denied before routing.

| Plan signal | Actual source and interpretation |
| --- | --- |
| Social preview latency/errors | Existing `http_server_requests_seconds_bucket/count`, GET `/api/v1/catalog/social-previews`; dashboard p95 and non-2xx ratio. Non-2xx includes expected validation/flagOFF denials; inspect status before treating as server incident. No new SLO threshold. |
| Media upload failures | Existing POST `/api/v1/media/uploads` and `/api/v1/media/{mediaId}/complete` HTTP counts by status; includes image/AD/video traffic and intentional422/429, not unique failed objects. Failed direct browser PUTs also require provider/client evidence; these bypass API HTTP metrics. |
| Registered media growth | `covie_media_ready_declared_bytes`: cached sum of declared sizes for READY, nondeleted metadata. Dashboard uses `max`, not sum across API replicas sharing one DB. This is an accounting proxy, **not physical storage usage**, pending/orphan object size, version history or billed bytes. |
| Actual storage growth/cost | Provider bucket usage/version/incomplete-upload inventory and billing, scoped to the configured bucket; collect through that provider's authenticated metrics/inventory outside the API and reconcile with registered bytes. No assumed universal S3 metric name or credentials; actual provider scrape/cost evidence remains external. Do not expose bucket administration publicly. |
| Video delivery errors | New `covie_catalog_video_delivery_failures_total`, no dynamic labels. Counts storage-open failures and body/close/output IO failures, including errors after headers/body commit. A206 alone cannot establish a complete delivery. It includes client disconnects; it is not exclusively corrupt objects or server faults. 404/416 policy/range denials remain HTTP metrics and do not increment this transport counter. |
| Moderation backlog | `covie_moderation_open_cases` and `covie_moderation_oldest_open_seconds`: all OPEN cases, not just Catalog; resolved/closed excluded. `max` across DB-sharing replicas avoids double count. No reporter, reason, caption or ID labels. |
| Moderation conflicts | HTTP POST moderation-case command counts409/412, grouped by route template/status; current endpoints may return409 only. Do not invent a412 transition. |
| Report rates | HTTP POST `/api/v1/reports` request rate, including retries/idempotent replays and rejected requests. This is submission traffic, not unique stored report count. Inspect status for accepted traffic. |
| Comment/report abuse | HTTP429 on comment/report POST routes; existing `covie_ratelimit_requests_total{group="REPORT",outcome="denied"}` for grouped report denials. Existing per-account Social comment limiter is distinct; no nonexistent SOCIAL_COMMENT rate-limit group is assumed. No user identifiers or bodies logged. |
| API5xx/429 and application errors | Pilot overview HTTP5xx, `covie_api_errors_total` bounded code/status, rate-limit denied/degraded panels. HTTP2xx is not a substitute for the delivery-failure counter. |
| Slow queries/DB pressure | Existing Hikari active/max/pending and API timer panels; actual operator `pg_stat_statements`/EXPLAIN evidence under performance runbook. SQL text is private diagnostic evidence, not a high-cardinality Prometheus label or public log. Do not enable statement/body logging merely to populate a dashboard. |
| Frontend LCP/CLS | Actual production-mode laboratory browser evidence in the Social acceptance reports. Not an application-exported RUM metric or field75th-percentile proof. Existing project has no approved RUM collector; no public telemetry endpoint/provider is invented. Production field observations remain external. |

## Refresh and failure semantics

Three new DB gauges reuse the existing database-metrics scheduled refresh
(`covie.metrics.database-refresh-ms`, default15000ms). Scraping reads the volatile
snapshot only, not the database. Before the first refresh or on an individual
query failure, that value is NaN, not a fabricated zero. Existing outbox/checkout
gauges and failure isolation remain unchanged. Zero is a legitimate fresh query
result for an empty backlog/READY inventory. Refresh does not change rows and
does not depend on publication/video flags, so historical media remains observable.

The byte aggregate scans qualifying metadata; measure this scheduled query on the
actual dataset before release. The existing local fixture is not a production
scale/refresh-cost guarantee. Tune the existing refresh setting based on evidence;
do not add speculative indexes or expensive storage calls to every scrape/request.

## Local validation versus rollout

Targeted PostgreSQL tests exercise exact case/media inclusion, full-row
preservation, cached reads after the owned fixture relation becomes unavailable,
and NaN refresh failure. Controller tests check open/truncated/committed-stream
errors without changing existing response contracts. Storage is mocked in these
error-boundary tests; real decoded/storage/browser evidence is separate.

Run `node infra/scripts/tests/social-observability-assets.test.mjs` for all ten
dashboard expressions' syntax and seven synthetic formula expectations using the
configured Prometheus3.6.0 promtool image with network disabled. This is not a live
Grafana rendering, provider scrape or alert-delivery rehearsal. Existing generic
alert thresholds are not changed by this dashboard. Do not send test notifications
to real webhook destinations or start normal observability services automatically.
