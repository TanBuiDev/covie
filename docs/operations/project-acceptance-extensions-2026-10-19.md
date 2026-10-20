# W01 — Social V2 and Seller Billing requirement reconciliation

Date: 2026-10-19, Asia/Saigon. Read-only reconciliation against main
`5ad071ffb9f3c2f9d7cfaf327c09726690dc3c1a`. This is an extension of the
project-wide acceptance index, not a declaration that the project is production-ready.
No provider request, database write, feature activation, test rerun, deployment or
Git publication was performed for this document. Existing dirty files are preserved.

## Status and evidence rules

- **PASS_LOCAL**: the named implementation and bounded recorded local proof exist.
  Retained evidence is not a newly executed test or an assertion that an old artifact
  contains later source changes. This includes scoped retained passes.
- **VERIFIED_LIVE**: only an actually observed live action, never inferred from a
  fake provider, signed fixture callback, successful CI or a return-page parameter.
- **CONFIRMED_GAP**: a demonstrated missing configuration/implementation relative
  to the requested operating mode. It is not automatically a code defect.
- **NOT_VERIFIED / EXTERNAL_INPUT**: actual evidence or human/infrastructure facts
  remain required. These do not reopen already closed local implementation work.
- **WAIVED**: Figma matching only. Physical devices, money settlement, deployment
  and legal facts are not silently waived with it.
- **HISTORICAL_SUPERSEDED**: older ACTIVE, missing-video, migration-collision and
  pre-integration branch snapshots have newer bounded dispositions below.

The two original plans and their detailed numbered matrices are incorporated by
reference; their individual assertions, named tests, logs and limitations remain
part of each row below. Grouped references cover each explicitly named subsection,
not a replacement of that subsection with one happy-path test. Optional suggestions,
example endpoint names and out-of-scope P1 ideas are not promoted to new requirements.

## Evidence index and source reconciliation

Paths below are repository-relative to `E:/WebApp/CoVie`.

| Key | Authoritative record / actual scope |
| --- | --- |
| S0 | `docs/plans/social-product-card/CoVie_Social_Product_Card_Ke_Hoach_Trien_Khai_Chi_Tiet_v1.0.md` — original sections 0–18 |
| S1 | `docs/plans/social-product-card/v2-local-acceptance-matrix-2026-10-17.md` — numbered requirements, evidence keys B/A/AU/C/S/P/R/U/V/VS/VU/VE/UX/CO/PERF/REC/FINAL |
| S2 | `docs/plans/social-product-card/v2-final-local-acceptance-2026-10-17.md`, `v2-observability-final-artifacts-progress-2026-10-17.md` — local closure, not current release artifact provenance |
| B0 | `docs/plans/seller-billing/CoVie-Seller-Billing-Subscription-Implementation-Plan-v1.0.md` — original sections 0–18, original BIL-001–030, A01–15 |
| B1 | `docs/plans/seller-billing/acceptance-original-bil-v1-local-2026-10-17.md` — original 30 rows and named method/run evidence; use final header, not superseded chronological counts |
| B2 | `docs/plans/seller-billing/local-whole-numbered-plan-acceptance-2026-10-19.md`, `local-final-acceptance-2026-10-19.md` — whole-numbered scope beyond the 30 BIL rows |
| H | `docs/operations/main-handoff-2026-10-19.md`, `migration-lineages.md`, `codeql-handoff-triage-2026-10-19.md` — source integration and security dispositions |
| C | `docs/superpowers/plans/2026-10-19-project-remaining-work.md` — recorded live-local baseline and W02–14 acceptance obligations; this document does not re-probe its live values |

Current HEAD was read directly with Git. A main-code path comparison from integrated
Social/Billing commit `d7301f79` to current HEAD, limited to API main source, Web
features, generated client and infra, identifies only
`apps/api/src/main/java/vn/covie/media/application/MediaProbeProcess.java` in that
path set. This is not a claim that build inputs/dependencies/test sources are identical:
H records AWS SDK 2.56.0, Next/eslint 16.4.1, Playwright 1.64.1, Zod 4.6.7,
merged migration lineages and combined contract checks; TypeScript remains 5.9.

H records the changed bounded probe-output reader, actual network-disabled MP4/WebM
probe/decode/invalid-file test 1 PASS in 7.815s, and remote candidate `efee4bf9`
full backend 1176 reported / 0 failures / 19 skips. Test-only Hide/Restore concurrency,
populated snapshot, outbox wait and Home summary isolation corrections have bounded
evidence there. These are not new business features or unresolved old red runs.
The handoff explicitly says older packaged runtime/JAR/image scans do **not** cover
the changed probe binary. Exact current immutable API/Web pair, image/SBOM scanning
and packaged smoke therefore remain W10 obligations, not a reason to repeat every
unchanged source test. No aggregate count is constructed from separate runs here.

Document SHA256 captured during this reconciliation:

| Key | SHA256 |
| --- | --- |
| S0 | `B7C6726E23B5183A06D9DA52F1066B5ABE692359E12A967862724230484555FB` |
| S1 | `952636C8C0FDC34916B5746926C3F2DFD7AF5D4E612968B00901789565CD6D5F` |
| B0 | `813679FD5301F4BB51330A37E38860EE14566842B5E736DB0DC871E561C5B261` |
| B1 | `63314175BD1D7D8BCA21C8468FAF37115142B53F91B7667467ABA2DB731A3778` |
| B2 numbered matrix | `C6A9BA46503FC4D2215BCDF389B07105963B4237BC5FAF61121770ED0AA2829E` |

## Social — original numbered scope

Java paths in this table start at `apps/api/src/main/java/vn/covie/`; Web paths
start at `apps/web/`. S1 supplies the fine-grained assertions and evidence for each
named group. G6 is the cross-cutting section 11–12 system/release gate, not an omitted
video scope or a fictitious extra implementation chapter.

| Original references | Existing implementation / evidence | Current disposition |
| --- | --- | --- |
| 0.1, 0.2, 0.3, 0.4 | Catalog-owned caption/media, existing commerce boundary; S1 G0/shared invariants | PASS_LOCAL; exclusions preserved, no independent social-post domain |
| 1.1, 1.2, 1.3, 1.4 | Baseline/source/API inventory; current generated contract; H integration | PASS_LOCAL; original GitHub baseline is historical, not current artifact identity |
| G0.1 | Route/schema/network/query inventory, S1 evidence B/A/P/U/PERF; H lineages | PASS_LOCAL; old unmerged migration collision HISTORICAL_SUPERSEDED |
| G0.2 | Six approved ADRs below; S1/B | PASS_LOCAL; approvals persist, do not ask again |
| G0.3 | Ten approved limits below; request validation, upload policy, preview/activity bounds | PASS_LOCAL under recorded configuration; no feature activation inferred |
| G0.4 | Recorded baseline/ADR gate; S1 | PASS_LOCAL; staging/exact deployed evidence remains separate |
| G1.1 | `admin/api/AdminCatalogController`, Admin read service/query/DTO; S1 A/FINAL | PASS_LOCAL including optional hasVideo and ordered private detail |
| G1.2 | Existing moderation case/open/action/audit/outbox handlers; S1 A/AU/R | PASS_LOCAL; approved SYSTEM_FLAG contract retained |
| G1.3 | `features/admin/admin-catalog-screen*`; S1 AU/UX | PASS_LOCAL search/filter/detail/history/dialog/409/focus, no fake moderation |
| G1.4, G1.5 | Admin auth/visibility/metadata/case effects; S1 AU/R/VE/FINAL | PASS_LOCAL; actual staging browser NOT_VERIFIED |
| G2.1 | Catalog caption/entity/Create/Edit and nullable migration; S1 C | PASS_LOCAL; original applied SQL identities retained by H |
| G2.2 | Existing routes/access/ETag/plaintext/READY/order/client; S1 C/R/V/H | PASS_LOCAL; mixed backend and existing commerce authority retained |
| G2.3 | Catalog Create/Edit gallery editor/draft preview/upload helpers; S1 S/VU/VE | PASS_LOCAL current UI, errors/retry/moderation feedback |
| G2.4, G2.5 | Three catalog types/caption/mixed/revocation/browser evidence; S1 C/S/UX/R/VE | PASS_LOCAL scoped cases, not every Cartesian combination |
| G3.1 | `social/application/CatalogSocialPreviewService`, batch controller; S1 P/V/PERF | PASS_LOCAL bounded eligible read/no N+1/no key exposure |
| G3.2 | Marketplace card/provider/Social content/comments; S1 P/U/CO/UX | PASS_LOCAL caption/gallery/commercial CTA/interactions/Ads/Catalog mode |
| G3.3 | Card/CSS/manual video controls; S1 UX/VU | PASS_LOCAL responsive/labels/loading/layout; Figma WAIVED; physical AT NOT_VERIFIED |
| G3.4, G3.5 | Duplicate/cross-account/batch/hide/interaction evidence; S1 P/U/CO/R/VE | PASS_LOCAL |
| G4.1 | `social/application/SocialActivityReadService`; S1 U | PASS_LOCAL current Like/Save/own-active-comment, not fabricated event audit |
| G4.2 | `/api/v1/me/social/activity`, actor-derived bounded keyset; S1 U/PERF | PASS_LOCAL; proposed original endpoint spelling is not a rename requirement |
| G4.3 | Profile/activity components and actual account flow; S1 U/UX | PASS_LOCAL tabs/pagination/states/accessibility; optional inline undo not mandatory |
| G4.4, G4.5 | Current visibility/cursor/account switch/Unlike/Unsave/delete; S1 U | PASS_LOCAL |
| G5.1 | `MediaPurpose.CATALOG_VIDEO`, separate flag; S1 V | PASS_LOCAL; no AD_BANNER-purpose reuse |
| G5.2 | Upload/probe/storage/attachment/content/cleanup policies; S1 V/VS/VE, H reader fix | PASS_LOCAL including real decode/deadline/leased cleanup/authorization/Range; current packaging W10 |
| G5.3 | Seller uploader/editor and shared manual player; S1 VU/VE | PASS_LOCAL order/retry/no autoplay/one playing card/pause/fallback |
| G5.4, G5.5 | Invalid/mixed/native/probe/cleanup/rollback evidence; S1 V/VS/VU/VE/REC | PASS_LOCAL; physical devices/WAN/provider CDN NOT_VERIFIED |
| 8.1, 8.2 | Catalog IDs/commerce/stock/reviews preserved, no duplicate authority; S1 CO/R/REC | PASS_LOCAL |
| 8.3 | Real membership/merchant/edit-HIDE/edit-RESTORE/comment races; S1 R/H | PASS_LOCAL; deterministic test scheduling does not alter production policy |
| 8.4 | Item moderation covers caption/media/video; public predicates; S1 V/VE/R | PASS_LOCAL; actual CDN behavior EXTERNAL_INPUT |
| 9 | Guest/Buyer/Seller outsider/Admin matrix; S1 A/C/U/V/R | PASS_LOCAL within named role/transport cases |
| 10 | Original small-PR proposal; actual H integration/PR39/40 | HISTORICAL_SUPERSEDED by reviewed integration; no need to recreate PR00–09 |
| 11.1, 11.2 | Targeted backend/components/provider tests; S1 and H | PASS_LOCAL named assertions, not one historical aggregate |
| 11.3 | Guest/Buyer/Seller/Admin/commerce/MP4/WebM actual chains; S1 CO/U/S/AU/VE/H | PASS_LOCAL; current dependency Seller publish flow 1 PASS/78.58s is DEV, not production render proof |
| 11.4 | S1 UX and named responsive/auth/network/security matrix | PASS_LOCAL scoped observations; Figma WAIVED; physical hardware/AT NOT_VERIFIED |
| 11.5 | S1 PERF/UX actual 20–40 cards/load/query/browser metrics | PASS_LOCAL local pilot, not deployed/field SLO or soak |
| 12.1 | S1 local regression/recovery + H current CI | PASS_LOCAL local/CI portions; staging/deployed runner/owner production signoff NOT_VERIFIED |
| 12.2 | Forward-only lineage/rollback manifest guards; S1 REC/H | PASS_LOCAL safe OFF fallback; old video-unaware binary actually incompatible, released reader floor EXTERNAL_INPUT |
| 12.3 | `docs/operations/social-v2-observability.md`, metrics/dashboard; S1 FINAL | PASS_LOCAL instrumentation/synthetic formulas; physical provider usage/cost/live alerts/field RUM NOT_VERIFIED |
| 12.4 / G6 | All named source/local gates above; H integration | PASS_LOCAL source/identified local scope; W10–13 actual artifact/deployment/monitoring gates NOT_VERIFIED |
| 13 | Existing package/toolchain commands, S1/H | PASS_LOCAL applicable commands, not permission to run destructive examples |
| 14 | S1 R/VS/V/REC and owner staging decision | PASS_LOCAL guards; real failures cannot be waived as success |
| 15 | Explicit F01–11 mapping below | PASS_LOCAL feature rows; F11 release portion NOT_VERIFIED |
| 16 | Scoped chronological reports/S1/S2/H | PASS_LOCAL evidence reporting; old counts remain historical |
| 17 | G0-only/stop-each-phase sample prompt | HISTORICAL_SUPERSEDED by approved whole-plan execution; no new deployment permission inferred |
| 18 | Reference/provenance links | PASS_LOCAL traceability, not a new feature |

### Social decisions, limits and functional IDs

| ADR | Binding requirement / evidence | Disposition |
| --- | --- | --- |
| ADR-A | One caption per Catalog item; S1 C | PASS_LOCAL; approved |
| ADR-B | Caption separate from description; S1 C/REC | PASS_LOCAL; approved |
| ADR-C | Preview batch only in Social mode; S1 P/CO | PASS_LOCAL; approved |
| ADR-D | Reuse item/comment moderation; S1 A/R/VE | PASS_LOCAL; approved |
| ADR-E | Ordered mixed gallery, independently gated video; S1 V/VU/VE | PASS_LOCAL; approved, normal video OFF |
| ADR-F | Activity represents currently valid state; S1 U | PASS_LOCAL; approved |

| Approved G0.3 limit | Evidence / disposition |
| --- | --- |
| Caption 3,000 UTF-16 units/plaintext/newlines | S1 C; PASS_LOCAL |
| Gallery at most 10 media | S1 V/VE; PASS_LOCAL new authoring, legacy image-only compatibility retained |
| At most one video/item | S1 V/VE; PASS_LOCAL |
| Video at most 50 MiB | S1 V/VE; PASS_LOCAL request and verified object boundaries |
| Video 60 seconds with reliable probe | S1 V/VE/H; PASS_LOCAL real probe, not filename/browser metadata |
| Feed preview first six media | S1 P/V; PASS_LOCAL |
| Default caption 3–5 lines and expand | S1 P/UX; PASS_LOCAL |
| Activity default 20 / maximum 50 | S1 U/PERF; PASS_LOCAL |
| Preview batch maximum 40 IDs | S1 P/PERF; PASS_LOCAL |
| Desktop Social two cards from 1024px | S1 UX; PASS_LOCAL bounded responsive observations |

| Original ID | Existing feature / evidence | Disposition |
| --- | --- | --- |
| F01 | Current LIKED activity; S1 U/G4 | PASS_LOCAL |
| F02 | Current SAVED activity; S1 U/G4 | PASS_LOCAL |
| F03 | Own COMMENTED activity; S1 U/G4 | PASS_LOCAL |
| F04 | Long literal caption/expand; S1 C/S/P | PASS_LOCAL |
| F05 | Ordered image gallery; S1 S/P | PASS_LOCAL |
| F06 | Verified mixed upload/manual video; S1 V/VS/VU/VE/H | PASS_LOCAL; not activated by Billing |
| F07 | Seller studio/preview/moderation; S1 C/S/VU | PASS_LOCAL |
| F08 | Admin real read/search/filter/metadata; S1 A/AU/FINAL | PASS_LOCAL |
| F09 | Admin moderation case/audit/visibility; S1 A/AU/R | PASS_LOCAL |
| F10 | Existing commerce retained; S1 CO/R/REC and H | PASS_LOCAL |
| F11 | CI/load/security/recovery/release safety; S1 PERF/UX/REC/FINAL/H | PASS_LOCAL local portions; current artifacts/deployed release/signoff NOT_VERIFIED |

## Billing — every original numbered section

Java locations start at `apps/api/src/main/java/vn/covie/billing/` unless specified.
B1 supplies named tests/logs for every BIL row; B2 covers obligations beyond them.

| Original references | Existing source/evidence | Current disposition |
| --- | --- | --- |
| 0.1, 0.2, 0.3 | Separate Seller subscription/Buyer orders; B1/B2 | PASS_LOCAL P0 invariants; no auto-debit/commission/payout added |
| 1.1, 1.2, 1.3 | Baseline/integration inventory; B2/H | PASS_LOCAL; proposed missing-source baseline HISTORICAL_SUPERSEDED |
| 2 | Versioned catalog/policy/settings; B2 + BIL013/014/020/024/028 | PASS_LOCAL defaults/mechanisms; actual commercial/tax/cohort facts EXTERNAL_INPUT |
| 3.1, 3.2, 3.3, 3.4 | Domain/state/access/finance policy; B2/B1 | PASS_LOCAL supported transitions; example suggested state names not new API obligations |
| 4.1 | Admin price/policy publication/history/current reads; B2 final Admin reports | PASS_LOCAL |
| 4.2 | Immutable snapshots/If-Match/audit/outbox/preview; BIL005/006/028/B2 | PASS_LOCAL; honest all-status preview is not future revenue prediction |
| 4.3 | `application/BillingPeriodCalculator`, quote/settlement/trial; BIL013/014/028 | PASS_LOCAL |
| 5 | V020–034 Billing originals plus validated combined lineages; B2/H | PASS_LOCAL schema/recovery; no SQL repair/reset/renumbering of applied history |
| 6.1 | `payos/PayosGateway`, private config/runbook | PASS_LOCAL adapter; C records live confirm-webhook only; provider account/channel/fees/terms EXTERNAL_INPUT |
| 6.2, 6.3 | Checkout/webhook/reconciliation/idempotency recovery; BIL007–012/023/029 | PASS_LOCAL signed fake-provider/PG scope; actual QR→bank→grant NOT_VERIFIED |
| 6.4 | Finance case independent roles/immutable evidence/manual runbook; BIL022 | PASS_LOCAL technical controls; actual refund/tax/VAT/legal EXTERNAL_INPUT |
| 7.1, 7.2 | Current generated contract/controllers/Seller and Admin client; B2/H | PASS_LOCAL actual compatible API, no rename to original proposed spelling |
| 8 | Entitlement/checkout/discovery/Need/Ads guards; BIL001–004/015–020/024/026/027/030 | PASS_LOCAL; current enforcementOFF is CONFIRMED_GAP for requested mandatory local operation |
| 9.1, 9.2 | `apps/web/features/seller/seller-billing-*`, merchant form; B2/BIL025 | PASS_LOCAL UI; exact route `/seller/{merchantId}/billing`; actual live journey NOT_VERIFIED |
| 10 | `apps/web/features/admin/admin-billing-screen*`, finance/plan/history/subscription reads; B2 | PASS_LOCAL; old audit-filter overlap must be rechecked before a code-defect claim |
| 11 including existing obligations | Legacy preview/shadow/idempotent grant/rollback; BIL020/026 | PASS_LOCAL mechanisms; actual cutoff/cohort/notice EXTERNAL_INPUT, never guessed/free-granted |
| 12 | All original 30 BIL rows below | PASS_LOCAL recorded scope, not 30 real-money cases |
| 13 G0 | Baseline/ADR/security/legal inventory; B2 | PASS_LOCAL technical decisions; provider/legal facts remain EXTERNAL_INPUT |
| 13 G1 | Core/database/settings/Admin/invariants; B2/B1 | PASS_LOCAL, combined lineage verification retained |
| 13 G2 | Adapter/QR/webhook/recovery/finance; B1/B2 | PASS_LOCAL fake-provider; live gateway enable alone does not close real payment |
| 13 G3 | Seller/Admin integration/accessibility; B2/BIL025 | PASS_LOCAL named native/component cases; Figma WAIVED |
| 13 G4 | Server enforcement/discovery/legacy/Ads; B1/B2 | PASS_LOCAL isolated ON fixtures; actual safe rollout W02 NOT_VERIFIED |
| 13 G5 | CI/local load/recovery/security/operations; B2/H | PASS_LOCAL named source gates; current immutable pair/deployed TLS/alerts/legal NOT_VERIFIED |
| 13 G6 | Controlled release/real settlement/reconciliation | NOT_VERIFIED; W03–05/W11–14, cannot be replaced by code approval |
| 14 | Actual billing/merchant/catalog/checkout/Need/Ads/settings/auth integration; B2/H | PASS_LOCAL file map, not an instruction to recreate suggested modules |
| 15 | Privacy/auth/metrics/plans/synthetic alerts/load/security; B2/H | PASS_LOCAL bounded evidence; dev advisory triage W09 and live delivery/field SLO W13 remain |
| 16 | A01–15 below, approved legacy409 ADR | PASS_LOCAL approved technical decisions; real facts remain external |
| 17 including final acceptance | Existing focused/full/source/contract/native reports; B2/H | PASS_LOCAL development, not production completion |
| 18 | Original references/runbooks/evidence links | PASS_LOCAL traceability |

### Original BIL-001–030 crosswalk

Each row inherits the **specific named test/method and run** in B1, with final
local closure B2 and combined source verification H. No local row below asserts
that corresponding real-world financial or deployed operational evidence exists.

| Original ID | Requirement / current code locus | Disposition and remaining scope |
| --- | --- | --- |
| BIL-001 | Buyer/guest free; entitlement/public/checkout/Social boundary | PASS_LOCAL |
| BIL-002 | One owner/two merchant invoices and entitlements isolated | PASS_LOCAL |
| BIL-003 | Unpaid draft permitted, publish/activate/sell denied | PASS_LOCAL ON fixture; current activation gap W02 |
| BIL-004 | Owner/member/foreign merchant/revocation financial boundaries | PASS_LOCAL |
| BIL-005 | Effective price revision and immutable old invoice | PASS_LOCAL |
| BIL-006 | Concurrent publication/If-Match one winner | PASS_LOCAL |
| BIL-007 | Expired/stale quote/provider outage/retry UI | PASS_LOCAL fake-provider; live W03–05 |
| BIL-008 | Same key/body replay, changed body409, legacy fail-closed | PASS_LOCAL approved legacy decision retained |
| BIL-009 | Callback dedup/reorder/retry grants once | PASS_LOCAL signed fixture; real settlement W04 |
| BIL-010 | Invalid signature/tampering/spoofed return cannot activate | PASS_LOCAL; C records actual bad-signature401, no live settlement proof |
| BIL-011 | Amount/currency mismatch cannot auto-settle | PASS_LOCAL signed fixture, no harmful real transfer required |
| BIL-012 | Unknown creation/lost callback/reconcile no double charge | PASS_LOCAL fake-provider; live invoice-specific read W05 |
| BIL-013 | Calendar/early/late/leap/UTC–ICT periods | PASS_LOCAL |
| BIL-014 | Trial OFF/ON/eligibility/concurrent abuse limit | PASS_LOCAL |
| BIL-015 | Grace read-only/new order denial/old fulfillment preserved | PASS_LOCAL |
| BIL-016 | Expiry during checkout inventory lock wait | PASS_LOCAL |
| BIL-017 | Paid entitlement never overrides moderation | PASS_LOCAL |
| BIL-018 | Search/Shop/Detail/Need availability without N+1 | PASS_LOCAL measured statement-count boundary |
| BIL-019 | Expiry preserves Social/review/order history | PASS_LOCAL |
| BIL-020 | Legacy replay/shadow/idempotent grants | PASS_LOCAL; actual cohort/cutoff EXTERNAL_INPUT |
| BIL-021 | Separated price/grant/reconcile permission and atomic audit | PASS_LOCAL; live audited reconciliation W05 |
| BIL-022 | Independent refund/reversal/dispute controls/runbook | PASS_LOCAL; actual external refund/legal not claimed |
| BIL-023 | Real disposable PostgreSQL crash/recovery/one settlement | PASS_LOCAL fixture; not real provider disaster exercise |
| BIL-024 | Registration switch affects new shops only | PASS_LOCAL |
| BIL-025 | Named responsive/UI states/keyboard/accessibility | PASS_LOCAL 360/390/desktop semantics; physical AT NOT_VERIFIED |
| BIL-026 | Enforcement kill switch preserves money/history/old orders | PASS_LOCAL; operational scope W02/W06 |
| BIL-027 | Seller payOS subscription separate from Buyer COD/negotiated bank | PASS_LOCAL |
| BIL-028 | Effective renewal/legacy pricing/nonretroactive notice | PASS_LOCAL; actual cohort communication EXTERNAL_INPUT |
| BIL-029 | Status/callback/export role/privacy/API-only secrets | PASS_LOCAL; current release artifact security W09–10 |
| BIL-030 | Expired merchant Ads technical pause/no invented refunds | PASS_LOCAL |

### Billing ADR A01–15 and approved legacy decision

| Original ADR | Current disposition / evidence |
| --- | --- |
| A01 draft vs ACTIVE | PASS_LOCAL draft creation with server entitlement boundary; BIL003; W02 activation distinct |
| A02 monthly merchant fee | PASS_LOCAL versioned seed/config; actual pilot plan/amount must be read and owner-selected, not assumed |
| A03 available cycles | PASS_LOCAL configured monthly scope; no automatic annual/multi-month sale added |
| A04 trial | PASS_LOCAL configurable OFF/default with scoped eligibility; BIL014 |
| A05 three-day read-only grace | PASS_LOCAL policy/old obligation preservation; BIL015 |
| A06 existing merchants | PASS_LOCAL preview/grant mechanism; actual cutoff/cohort EXTERNAL_INPUT W02 |
| A07 payment channel | PASS_LOCAL payOS adapter/no auto-debit; actual account terms/fees EXTERNAL_INPUT W03/W11 |
| A08 tax/invoice | EXTERNAL_INPUT legal entity/VAT/invoicing facts; broad technical approval is not legal evidence |
| A09 refund | PASS_LOCAL independent manual case workflow/runbook; external transfer not performed |
| A10 Ads expiry | PASS_LOCAL approved technical pause ADR `docs/architecture/adr-seller-billing-ads-expiry.md`; BIL030 |
| A11 existing subscriber price | PASS_LOCAL effective revision/nonretroactive notice; BIL028 |
| A12 resale | PASS_LOCAL merchant catalog entitlement; free C2C remains outside this plan |
| A13 staff/manager | PASS_LOCAL server-scoped owner finance authority/role boundaries; BIL004 |
| A14 promotion/free grant | PASS_LOCAL controlled legacy grant; general promotion P1 not mandatory new scope |
| A15 cancellation | PASS_LOCAL cancel future renewal only, no P0 auto-renew/no deletion of paid period |
| Later legacy replay decision | PASS_LOCAL unverifiable legacy409/status recovery, no new charge; fully bound new replay succeeds; `docs/architecture/adr-seller-billing-legacy-replay.md`/BIL008 |

## Current live baseline, concrete gap and remaining evidence

C records creation metric **1**, reconciliation metric **0**, policy PAY_BEFORE_SELL
with trial0/grace3, no GLOBAL enforcement override and registry default **false**.
These are recorded baseline observations, not measurements repeated by this audit.
Source directly confirms `SystemSettingRegistry` default false and
`BillingEntitlementService` reads **GLOBAL-only** enforcement. Merchant form checks
`rules.enforcementEnabled && rules.onboardingMode === "PAY_BEFORE_SELL"` before
redirecting to `/seller/{merchantId}/billing`. The dashboard screenshot alone is
therefore **not evidence of a missing FE/BE payment implementation**.

| Current item | Classification | Closure / plan owner |
| --- | --- | --- |
| Mandatory Seller payment not enforced in current reported local mode | CONFIRMED_GAP configuration, not proven code bug | W02 safe scope/cohort/backup/Admin API activation then verify unpaid denial and onboarding; no uncontrolled GLOBAL cutover |
| Live QR/invoice created from exact Seller UI and safe reload | NOT_VERIFIED | W03 owner-selected merchant/plan/amount/budget; one controlled invoice |
| Actual bank payment→signed callback→PAID→one grant→open selling | NOT_VERIFIED / human payment input | W04; agent cannot fabricate or perform owner's transfer |
| Live invoice-specific reconcile/read, no extra grant | NOT_VERIFIED | W05 audited authorized action; workerOFF is not itself a code defect |
| Tunnel lifecycle/secret exposure/pending-payment shutdown | NOT_VERIFIED operational closure | W06; temporary HTTPS callback is not deployed hosting |
| Audit filter overlap1280 | NOT_VERIFIED current reproduction | W07; old measured report is not current confirmed defect |
| Current post-probe-change immutable API/Web pair/SBOM/image scan | NOT_VERIFIED artifact provenance | W10; old source/JAR scan cannot substitute |
| Dev-only high Braces advisory | Recorded finding requiring current triage | W09; not automatically runtime-high exposure |
| Stable deploy domain, PSP/legal facts, released reader floor, infrastructure | EXTERNAL_INPUT | W11–12; inventory actual inputs, no imagined environment |
| Actual monitoring/alert delivery/provider bytes/cost/field RUM/device/WAN | NOT_VERIFIED / EXTERNAL_INPUT | W13; metadata bytes/lab CLS/synthetic formulas are narrower evidence |
| Honest final acceptance, rollback and operational ownership | NOT_VERIFIED final handoff | W14 after preceding scoped evidence, not blanket completion |

## Coverage conclusion

Inventory includes **both original section sets 0–18 (19 each)**, all explicitly
listed subsections grouped above, **Social G0–G6, six ADRs, ten limit rows, eleven
F IDs**, and **Billing seven gates, fifteen A IDs and thirty original BIL IDs**.
The detailed requirement assertions are linked to S1/B1/B2 rather than discarded.
This is coverage of the two extensions, not the project's separate Plan01–10 inventory.

No additional missing Social/Billing implementation was demonstrated by this
read-only reconciliation. That is not a zero-defect guarantee. The confirmed current
configuration gap and unverified live/artifact/production evidence remain open under
W02–14. Old staging-blocks-local, missing-video, open-branch and migration-collision
snapshots are HISTORICAL_SUPERSEDED; Figma matching remains WAIVED. No unchanged
test was rerun merely to populate this matrix, no feature flags were changed, and
no old artifact was relabelled current.
