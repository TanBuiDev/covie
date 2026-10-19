# Social V2 — observability and final local artifacts

Date:2026-10-17. Previous turn made actual Admin metadata/migration/recovery progress. This checkpoint implements the remaining literal plan12.3 local monitoring coverage and verifies final-source artifacts. It is not a staging/production rollout.

## Evidenced observability gaps and minimal changes

Existing HTTP histograms/error/rate-limit/job metrics already cover request latency/status, upload/complete requests, report submissions and abuse denials. Do not duplicate those counters or add a public telemetry API.

Three cached, tag-free gauges added to existing `DatabaseOperationalMetrics`: OPEN moderation case count, oldest OPEN case age, and declared bytes in nondeleted READY media metadata. They reuse the existing15s scheduled refresh; reads/scrapes access the snapshot, and failed queries return NaN rather than fabricated zero. The byte metric is **not provider physical/versioned/billed storage usage**. Existing outbox/checkout logic remains unchanged. No migration/index/business state change.

The video controller previously had no signal for storage/body delivery failure after a response was committed. Generic HTTP status is insufficient for partial deliveries. Add one tag-free `covie.catalog.video.delivery.failures` counter for storage-open ApiException and stream/output/close IOException paths; existing response handling, range/auth/visibility/business contracts stay unchanged. Client disconnects are included, not mislabeled as exclusively corrupt objects.

New `infra/observability/grafana/dashboards/covie-social-v2.json` supplies ten panels using route-template labels and existing/new metrics. No new alert thresholds or SLO inferred from localhost. `docs/operations/social-v2-observability.md` maps every12.3 signal, cache/failure semantics and limits, including provider inventory/cost and frontend field measurements remaining external. Configured Grafana file provider already loads the dashboard directory.

## Focused RED/GREEN and boundaries

- Initial new test compile failure was harness-only (`SimpleMeterRegistry` is not AutoCloseable), `.local/social-v2-observability-red.log`; use existing manual-finally-close pattern.
- Verified RED `.local/social-v2-observability-red-verified.log`:2cases,1FAIL missing delivery counter/1ERROR missing required gauge. These are actual missing instrumentation, not broken SQL/HTTP behavior.
- `.local/social-v2-observability-green.log`:7PASS/0skip,36.067s Maven. New realPG gauge case, retained outbox metrics regression, affected video content class and featureOFF guard. LivePG tests preserve case/media JSONB, exclude resolved/closed/deleted/nonREADY, confirm cached reads and NaN refresh after an **owned disposable fixture** relation is removed. No original/recovered database used. Storage mocked only in content error tests.
- Added committed-body case initially failed on test's assumption that MockMvc propagates IOException. Existing global advice resolves it. Test-only correction asserts resolved IOException, committed response with at least8192bytes and counter+1/no labels. `.local/social-v2-observability-committed-body-final.log`:1PASS/0skip,16.11s test/25.321s Maven. No new response-contract fix and no8case aggregate claim.
- Actual JVM production-profile/freshPG18/Redis8/generated-key context with real validators and HTTP Prometheus scrape: `.local/social-v2-observability-prod-runtime.log`,1PASS/0skip,17.49s test/26.578s Maven. Four actual exported metric names observed; initial empty fixture values0. Not a deployed Prometheus/provider/Grafana path.
- New `infra/scripts/tests/social-observability-assets.test.mjs` uses the configured Prometheus image, no-network promtool containers and guarded temporary-directory cleanup. All ten actual dashboard expressions parse, seven selected synthetic rate/ratio/histogram/gauge formulas pass. `.local/social-v2-observability-dashboard-configured.log`,exit0. Initial regex escaping was corrected in the dashboard; floating-point exact-comparison/rounding fixture failures corrected with an explicit absolute1e-9 comparison of unchanged actual expressions. Not a production threshold or fake metric sample. Monitoring validator calls this focused gate; shell syntax checked, old full monitoring/alert suite not replayed. Grafana rendering/alert delivery not claimed.

Lambda parameter names were subsequently made descriptive without behavior changes; final Docker javac compilation below includes those exact sources. No unchanged functional workflow was replayed for that naming-only edit.

## Final exact-source builds

Fresh independent whitelist contexts:1141API and598Web files. Each source/context SHA256 matches before/after build/runtime; independently enumerated source and context path sets have zero delta. Evidence:

- `.local/social-v2-observability-final-provenance.json`
- `.local/social-v2-final-independent-source-inventory.json`
- `.local/social-v2-g5-runtime-image-observability-final-{source-manifest,source-check,result,inspect}.json`
- `.local/social-v2-final-web-metadata-final-{source-manifest,source-check,result,inspect}.json`

| Artifact | Actual result |
| --- | --- |
| API | `covie-api:social-v2-video-runtime-20261017-observability-final`, ID `sha256:e118462e68a426a43a36d00e75c6c707af088ba519d38cd5f70ec95ee029fa31`,685696660bytes,UID10001:10001 |
| API build | `...runtime-image-observability-final-build.log`,exit0;811main/290test Java sources compiled, **tests skipped**,35.635s Maven package. Metadata repository401warnings did not fail packaging and are not product test failures |
| Web | `covie-web:social-v2-video-final-local-20261017-metadata-final`, ID `sha256:73d33f1146b4bb64ff6a91b1d144d6945b03f49a5b8f35757727e7d6ecf6b2c8`,406055991bytes,UID10001:10001 |
| Web build | `.local/social-v2-final-web-metadata-final-build.log`,exit0;configured Node24/Next16.4,compile5.1s/TypeScript12.1s/37routes. Normal feature flags OFF |
| Static API and actual Web verifier | `.local/social-v2-observability-images-static-web-runtime.log`,exit0;known forbidden-artifact/nonroot checks, homepage+four Web assets200. API runtime explicitly skipped in this static verifier, separately proved below |

These are local images, not registry/released digests, signatures, pushed artifacts or exhaustive secret/CVE/license verification. Previous c315…/979344… snapshots remain historical, not overwritten or called current. Production-mode Social CLS evidence remains applicable to unchanged feed/loading source; no claim the defaultOFF final Web image has active Social/video features.

## Actual final-image boot and targeted media read

`.local/social-v2-observability-image-health.log`,exit0; fixture `.local/social-v2-observability-image-health-1b7970a8323f/result.json`,completedTRUE. Actual new API image PROD boot13.531s with freshPG18/Redis8/generated RSA3072/runtime env, no validator bypass,25successful Flyway migrations. Customhealth/readiness/liveness/aggregate200UP,nonroot10001,RedisPONG/config retained,all four real metric samples0. Owned resources/secrets removed.

Since the video controller gained instrumentation and the Admin summary gained a field after the previous image, run **only** a focused final-artifact public read/metadata observation, not old upload/publish/commerce journeys:

`.local/social-v2-final-artifact-native-read.log`,exit0; fixture `.local/social-v2-video-chain-f0f19c60bdef`,completedTRUE. Explicit imageID/manifest bound before/after. Actual packaged probe/full decode of generated160×120H264/AAC4000ms MP4, SDK objects then seededREADY VIDEO0/IMAGE1-primary/IMAGE2. Actual current copied Next/Chrome155.0.8059.84/1280×1000 public detail click→native decode/time>0.1,actual206/46001bytes,pageErrors0. Screenshot `public-detail-video.png` **VIEWED**. Native browser is dev-mode validation, not the production Web verifier above; not a repeated signed-upload proof.

Actual Admin issued-session HTTP summary200/`hasVideo:true`. Current image scrape after fixture refresh: declaredREADYbytes46207,deliveryFailures0,moderation count/age0. Those bytes are metadata, not independently enumerated provider usage. Six protected commerce tables fullJSONB unchanged; itemversion0/caption/description/orderedlinks retained. Auth login writes are legitimate and not called zero-global-writes.

Earlier actual MP4/WebM Seller signedPUT→complete/probeREADY→mixedCreate/reload/Publish→guest playback/moderation chains remain in their reports on unchanged underlying upload/mixed/player paths; this final read supplements, never replaces, them.

## Source secrecy and cleanup

Native Gitleaks8.30.1 scans both exact build contexts, redacted output: `.local/social-v2-final-{api,web}-source-secrets.log/.json`,both exit0/no findings,~3.57/3.48MB. This is source-context secret detection, not runtime-image dependency/CVE scanning or repository-history review. Configured remote release/security jobs still require the actual immutable PR head and are not fabricated as run locally.

`.local/social-v2-final-acceptance-cleanup.json`:62previously referenced reports exist,zero owned containers,normal/verification listeners and generated fixture secrets. All test/build sessions terminal; original.next/existing-recovered data/normal services untouched. HEAD/main unchanged; no commit/push/remote/activation/deploy. Normal flags OFF.

Next action: Close the full local acceptance matrix against these final artifacts and retained scoped evidence, then hand off without enabling or deploying features.
Required from you: Nothing
Completion condition: No required local implementation/verification item remains open; external release facts remain explicitly unperformed.
