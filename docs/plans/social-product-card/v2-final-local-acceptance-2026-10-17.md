# Social Product Card 2.0 — final local acceptance

Date:2026-10-17. Scope: **all G0–G6 local implementation and local verification** from the approved detailed plan, including video/mixed media. Staging as a prerequisite and Figma matching were waived by the owner; production, remote Git/CI and release activation were not authorized or performed. Feature approvals persist but do not manufacture those operating facts.

Requirement-by-requirement matrix: `v2-local-acceptance-matrix-2026-10-17.md`. Baseline commit `803b25b89f4686b4135b0de5ef101aabcc84b614`; current main/HEAD `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`, implementation remains uncommitted in the existing dirty tree. No remote delivery claim.

## Outcome by requirement

| Requirements | Implemented and locally verified |
| --- | --- |
|G0/ADR/limits | Existing Catalog authority,one caption/item,separate description,Social-only batch,current-state activity,mixed order,separate defaultOFF flags; approved limits validated without expanding business scope |
|F08–09/G1 Admin | Actual guarded listing/detail/search/filter/paging/case history,caption/video presence; audited idempotent SYSTEM_FLAG opening and existing moderation-case commands,conflicts/public effects,read-only/private fields |
|F04–05/F07/G2 Seller | Three Catalog types,caption presence/clear/Unicode/plaintext/If-Match,ordered image/video authoring,shared preview,drafts/navigation/errors,moderation reasons/feedback and preserved commerce fields |
|G3 Feed | Caption clamp/expand,ordered gallery/fallback/keyboard/swipe,server-confirmed interactions,lazy comments,guest/account/hide reconciliation,Ads/Search/Catalog behavior preserved |
|F01–03/G4 Activity | Own current LIKED/SAVED/ACTIVE comments,visibility/keyset/type/limits,cross-account guards,URL tabs/append/retry/profile entry; no fabricated full event history |
|F06/G5 Video | Dedicated purpose/permissions/quota,50MiB/60s,real metadata/full decode beforeREADY,bounded object/process/probe transfer,idempotency/retry,mixed attach/order/cover,opaque authorized ranges,manual native playback/pause/exclusivity/fallback,moderation,leased orphan cleanup/retry/race protection |
|F10/G0–G6 consistency | Actual protected-row checks and named Social→Cart→Checkout→Seller fulfillment→Buyer receipt→verified review chain; lock contention for membership/merchant/edit-hide/restore/comment-delete,not a broad commerce refactor |
|G6 local evidence | Actual API/Web builds,current-source manifests,OpenAPI/client zero unrelated drift,targeted BE/Web/security tests,actual roles/browser/storage flows,four widths/accessibility/offline,local API/browser/query measurements,current backup/restore and safe rollback handling |
|12.3 monitoring | Bounded-label cached moderation/media gauges and delivery-failure counter,existing HTTP/abuse/job sources,ten-panel dashboard/promtool formula checks,actual PROD-profile and packaged-image scrape,operator signal/limit mapping |

The matrix and62+scoped reports identify source locations,individual run counts/commands/logs/screenshots and limits. They are not summed into an invented full-suite result. Already-passed unchanged source paths were not repeatedly retested.

## Corrections discovered during final reconciliation

- Undersized Social preview-loading geometry caused actual production-mode CLS0.42694; loading-only reservation fix reduced the measured four-width workload to0.0166–0.0248,without changing ready content or business behavior.
- Old pre-video binary boots but cannot hydrate CATALOG_VIDEO; actual Seller500 despite healthy boot. Preserve data/current compatible binary with videoOFF; source-bound verified-target/evidence metadata now fails closed before unsafe rollback. Explicit expand/activate compatible-reader floor documented. **Old binary is not called compatible**.
- Historical V022 migration-test stage accidentally targeted latest; actual1expected/4executed failure corrected by pinning022 and explicitly checking023–025 preservation. No applied migration modified.
- Deferred G1 `hasVideo` indicator was genuinely missing after video implementation; minimal existing-query boolean/optional contract/Admin label now verified.
- Required moderation/media/delivery observability was absent; minimal instrumentation/config added,with no new public endpoint,business state change or speculative index.

## Final-source validation

Latest complete details: `v2-observability-final-artifacts-progress-2026-10-17.md` and `v2-final-metadata-migration-recovery-progress-2026-10-17.md`.

- API1141/Web598 source+context files match SHA256 and independent path sets,zero delta. API ID `sha256:e118462e68a426a43a36d00e75c6c707af088ba519d38cd5f70ec95ee029fa31`; Web ID `sha256:73d33f1146b4bb64ff6a91b1d144d6945b03f49a5b8f35757727e7d6ecf6b2c8`. Both local/nonroot10001:10001,not pushed release identities.
- API packaging compiles811main/290test sources,**tests skipped during image build**. Actual current-source targeted test runs are recorded separately; compilation is not290PASS.
- Actual new image PROD boot/freshPG18Redis8/Flyway001–025/custom health and actuator health/scrape PASS. Web production build37routes/typecheck and actual homepage/four assets200 PASS.
- Current optional `AdminCatalogItemSummaryResponse.hasVideo` is the only final metadata-contract delta;148paths/212schemas. Actual export/generated client/check,affected Web typecheck/lint and Admin component tests pass. Observability changes do not alter the REST contract.
- Actual final packaged-probe/SDK-storage/seededREADY/native guest206/46001bytes/decoded playback and AdminHTTP200hasVideo observed,deliveryFailures0/registeredREADYbytes46207; six protected tables unchanged. This complements earlier real upload/publishMP4/WebM/moderation journeys,not a substituted mock or a new upload claim.
- Current populated upgrade and canonicalV025 mixed-metadata dump/checksum/restore compare old fields/full nonstatic public-table rows; previous actual Social recovery and rollback-preservation evidence retained. No provider-object/offhost recovery claim.
- Source-context Gitleaks8.30.1 scans exit0/no findings; targeted IDOR/CSRF/XSS/MIME/quota/denial proof retained. Not an exhaustive runtime CVE/CodeQL/license guarantee or a claim prior unrelated security findings were resolved.
- Owned containers/networks/processes/generated credentials removed; normal ports/services/original.next/existing-recovered data untouched. No source secrets/environment files copied into build contexts.

## Explicitly not performed / activation constraints

- Actual staging Phase4 runner,provider/CDN/private image policy in a deployed environment,WAN/physical Android/iOS,field75thLCP/CLS,representative production load/SLO/cost/refresh overhead and monitoring delivery remain external evidence. Registered bytes are only metadata accounting,not physical bucket usage/billing.
- No remote PR/merge/commit/push,exact-head remote CI/CodeQL/release scans,registry push/signing,real released B-A-B digest drill,production backup/offhost-object recovery or production signoff/deploy. Do not relabel them PASS.
- Legacy pre-video binary remains unsuitable after Catalog video records exist. Do not force that rollback,drop new columns/delete user data or bulk-delete media. Establish and verify the compatible-reader release floor before eventual activation.
- All normal V2/video flags remain OFF. Local completion is **not permission or proof to activate or release**. Release requires its actual environment evidence and separate operator process.

Next action: Hand off this local acceptance matrix and preserve the OFF/uncommitted state; perform no release operation without a new authorized request.
Required from you: Nothing
Completion condition: The approved full local scope is implemented and evidenced; external deployment facts remain explicitly unperformed.
