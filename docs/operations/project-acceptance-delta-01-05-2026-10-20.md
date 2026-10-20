# Plan01–05 acceptance reconciliation delta — 2026-10-20

**Subsequent scoped verification:** Q01/Q02/Q03 below now have10PASS service/evaluator assertions in `.local/checkout-shipping-explicit-boundaries-20261020.log`, production source unchanged. Q04 remains NOT_VERIFIED; syntax-check tool call blocked before execution. See `local-evidence-security-handoff-2026-10-20.md`. Original census rows retain their read-only historical scope.

Scope: targeted W01/W08 requirement-to-evidence reconciliation. Baseline source is `5ad071ffb9f3c2f9d7cfaf327c09726690dc3c1a` plus the explicitly recorded local test/Docker/Need deltas; no new application changes or executions in this audit.

## What this delta changes

- The prior `project-acceptance-foundation-2026-10-19.md` indexed86Tasks/625Steps but counted only37 Markdown self-review boxes. It omitted fenced-text acceptance statements from its checklist count. This delta enumerates **208 explicit acceptance/self-review/spec-coverage statements**: Plan01=31, Plan02=51, Plan03=31, Plan04=44, Plan05=51. The additional171 statements were not171 missing features; they were not parsed by the earlier rule.
- Master decomposition/feature DoD contributes **69 separate traceability statements** below. These often restate the detailed-plan requirements. Do not sum overlap into an invented bug count.
- Historical recipe steps (`write RED`, install pinned old version, create a branch, commit, initial migration ends atV006/V009/V010) are not current business defects. Current source can satisfy their invariant by later implementation/tests without reproducing that recipe. Never reset existing volumes or repair applied migration history to recreate it.
- PASS_LOCAL below means **retained, bounded named local assertions** mapped to the evidence index, not newly executed tests, complete future deployment or every line of surrounding normative prose. Source inspections and recorded results are kept distinct. A NOT_VERIFIED assertion is not a proven production bug.
- Latest owner-authorized local Billing payment/release/reconciliation/enforcementON state is acknowledged from `project-acceptance-current-2026-10-19.md`; this audit reads no keys/provider payloads and changes no flag. Earlier OFF text in historical reports is not current activation authority. Social/video and production remain separate.

## Exact new evidence gaps, not code-defect claims

| ID | Original requirement | Current direct evidence / precise missing assertion | Disposition / smallest justified follow-up |
|---|---|---|---|
| Q01 | Plan04Task13; acceptance original line2892: expired token rejected | `CheckoutSessionService.java:3` rejects expiry `!expiresAt.isAfter(clock.now())`; current `CheckoutSessionServiceTest.java:25` asserts only saved ID. Repository-wide Java test search found no `CHECKOUT_SESSION_EXPIRED` assertion. Foreign token and consumed replay already proven `CheckoutHttpAcceptanceTest.java:472/525`. | NOT_VERIFIED: exact deadline and immediately-after rejection while just-before remains usable. One service-level boundary test is justified by this original acceptance requirement; no production fix supported by current source. |
| Q02 | Plan04Task13; acceptance original line2891: opaque, hashed-at-rest,15-minute session token | Source generates opaque raw token, stores `hasher.hash(raw)` and computes expiry from AppClock+configuredTTL. Existing service test constructs15min but checks only returned ID, not hash/expiry; no other explicit checkout-token hash/TTL persistence assertion located. Auth refresh hash tests are NOT checkout-token proof. | NOT_VERIFIED: assert persisted hash differs from raw/matches actual hasher and persisted/returned expiry equals controlled now+15min. Reuse the existing captor fixture; do not rerun consumed-token race or write new crypto machinery. |
| Q03 | Plan04Task5; acceptance original line2874: conditional ANY/ALL boundaries tested | `ShippingEvaluator.java:8` implements ALL/ANY and inclusive amount>= /distance<=. Existing `ShippingPolicySnapshotFactoryTest.java:38` uses one ANY/DISTANCE_MAX900<1000 rule. Repository-wide Java test search found zero `ShippingRuleMatchMode.ALL`. | NOT_VERIFIED: focused pure evaluator assertions at/below/above the two thresholds, ANY vs ALL with enabled rules. This is an original named condition, not speculative test expansion. Current code is not demonstrated wrong. |
| Q04 | Plan01 deliverable original59; Task10Step2/Task12Step8: actual `nginx -t` | Current `verify-nginx-prod.sh` is a static content verifier.2026-10-12 operational receipt records static Bash/Forwarded checks;2026-08-25 old binary syntax evidence predates current config. No newer actual current-config Nginx-binary syntax receipt located. | NOT_VERIFIED current binary gate; consolidate with W10/W11 ingress verification. An isolated syntax check does not require production deployment or full BE. No source defect established. |

Q01/Q02 overlap one existing checkout-session test surface, not two product bugs. Q03 is a missing explicitly required boundary-test assertion, not evidence of wrong shipping calculation. This is a bounded list found by direct source/test searches, not a claim that all normative prose has been exhaustively proven.

## Already closed or retained — do not reopen

- Original Plan05Task18 seven pairs: confirm/pending-cancel and confirm/reject at `OrderCancellationControllerIntegrationTest.java:413/432`; buyer/auto-complete at:449; complete/cancel at:294; complete/mark-delivered at:351; cancellation accept/reject and issue/auto-complete in `OrderCommandSoakConcurrencyTest.java:105/151`. The latest three are9PASS/0skip,3repeats each, actual held PG row lock +both waiters before release, JWT HTTP for actors and actual selected-order worker. Isolated log: `C:/Users/tanbu/.codex/worktrees/handoff-integration/CoVie/.local/project-remaining-three-races-offline.log`; execution receipt states36.522s,2026-10-20T01:15:02+07. This is not new full BE or scheduler due-clock proof.
- Coverage OR is NOT missing: `PilotFixtureIntegrationTest.java:225–252` explicitly compares radius, area and both-enabled hybrid; dynamic-contract inventory retains actual pilot PostgreSQL proof. The smaller `CheckoutDeliveryCoverageIntegrationTest` alone does not show that full scope, but cannot erase the pilot evidence.
- Discovery nearest location before price-sort at `CatalogDiscoveryRepositoryIntegrationTest.java:86`, tie-safe all-sort keysets:263, weekly/exception hours:294; do not add duplicate geography/cursor tests.
- Inventory per-item mixed outcome/replay/below-reserved/ledger at `MerchantCatalogEditControllerIntegrationTest.java:658/723`; do not infer missing stock proof because tests live under catalog rather than inventory.
- Plan02 Argon2 configured tuple is directly matched from `PasswordConfiguration.java:15` to original Plan02 line32 (16-byte salt,32-byte hash,parallelism1,memory19456KiB,iterations2), and `PasswordConfigurationTest.java:12` proves versioned prefix and matches/non-match. This is retained source+test evidence, not a new test gap merely because the test does not repeat every literal.
- Auth registration/identity/recovery/strict refresh/IDOR/CSRF proofs are retained in named tests below. Live Google, real host HTTPS/cookies/storage and all-current-screen P0 browser acceptance remain EXTERNAL_INPUT/NOT_VERIFIED by their own scope; local provider fixture proof must not be relabeled VERIFIED_LIVE.
- Figma matching is WAIVED by owner. Historical201captures/manual PASS ledger does not prove current source screenshots and is not a reason to redesign upgraded UI.

## Evidence anchors

Retained execution sources: `docs/operations/main-handoff-2026-10-19.md` completed receipt; `docs/backend-progress-review-2026-10-12.md` scope correction; `docs/backend-evidence-matrix-2026-10-11.md`; `docs/backend-dynamic-contract-inventory-2026-10-09.md`; `docs/backend-operational-gates-2026-10-12.md`; `docs/operations/project-remaining-work-progress-2026-10-19.md` dated actual2026-10-20. Older ACTIVE/partial paragraphs yield to their later source-bound results, not to an unsupported blanket completion assertion.

| Key | Scope | Actual source/test anchor | Retained proof and limit |
|---|---|---|---|
| F | Foundation/schema/runtime | `README.md`; `infra/compose/compose.local.yml`; `apps/api/src/main/resources/db/migration/V001__extensions_and_foundation.sql`; `FoundationMigrationTest`, `CovieApplicationTest`; combined `MigrationLineageIntegrationTest` | Retain actual local/CI foundation/migration proof from main-handoff and operational gates. Empty/partial Billing034/Social025 histories validated without checksum rewriting. Current packaged API health/Flyway40 proof is in the2026-10-20 execution receipt; not a clean-clone production deployment. |
| P | Primitives/ports | `Money.java`, `UuidV7Generator.java`, `AppClock.java`, `CurrentActor.java`, `OutboxEventWriter.java`; `MoneyTest`, `UuidV7GeneratorTest`, `AppClockTest`, `CurrentActorTest`, `OutboxEventIntentTest` | Source and focused tests retain integer monetary representation/version7/time/global actor/intent semantics; stage-only port architecture has since gained real adapters, not fake no-op. |
| R | API/error/correlation | `RequestIdFilter.java`, `GlobalExceptionHandler.java`; `HealthControllerTest`; `AuthCookieCsrfAcceptanceTest`; OpenAPI contracts | Retain exact health/request ID/error serialization assertions; external ingress remains separate. |
| C | Generated contract/CI | `packages/api-client`; `apps/api/src/test/java/vn/covie/openapi`; `.github/workflows/ci.yml`; `infra/scripts/verify-generated-client.sh` | PR39 source-bound backend1176reported/0fail/19skip and frontend/contract gates plus current checked generated client. These are run facts, not every original assertion or live-provider proof. |
| S | Security hygiene | `AuthAuditSafetyTest.java:39`, `AuthDatabaseInvariantTest.java:45`; `.gitignore`; `.env.example`; handoff security receipts | Actual distinctive-secret log/audit assertions, raw-refresh absence from all character columns, and bounded source/history scan disposition retained. Does not certify every future log or dependency. No secret read/copied here. |
| N | Nginx/same-domain | `infra/docker/nginx.conf`; `infra/docker/nginx.prod.conf`; `infra/scripts/verify-nginx-prod.sh` | Static route/Actuator/Forwarded checks retained. Current actual Nginx-binary `-t` receipt not located; older2026-08-25 syntax proof predates current ingress source. |
| A | Auth/OIDC/onboarding/login | `auth/application/GoogleAuthService.java`, `AuthApplicationService.java`, `EmailPasswordLoginService.java`; `AuthAcceptanceTest.java:77`, `:112`, `:128`; `GoogleAuthServiceTest`; `AuthOnboardingIntegrationTest`; `EmailPasswordLoginServiceTest` | Actual local OIDC success-handler/claim fixture→onboarding/issued JWT and returning Google/email identity/recovery assertions retained. Does not establish live Google network/provider authorization. |
| J | JWT/password/token configuration | `JwtTokenServiceTest.java:25` (required claims/RSA and15-minute expiresAt); `JwtConfigurationTest`; `PasswordConfigurationTest.java:12`; `OpaqueTokenGeneratorTest` | RSA/JWT expiry/versioned Argon2id round-trip and opaque token generation retained. Exact configured tuple is source-proven in `PasswordConfiguration.java:15`: salt16/hash32/parallelism1/memory19456/iterations2, matching original Plan02 line32; no invented requirement to duplicate that literal as a new test. |
| T | Refresh/session/CSRF | `AuthAcceptanceTest.java:148`, `:168`, `:188`, `:208`, `:239`; `AuthIdorAcceptanceTest.java:137`, `:149`; `AuthCookieCsrfAcceptanceTest.java:67`, `:109` | Rotation keeps initial expiry, strict family reuse/races, independent devices, active account/session checks, cookie/Bearer/CSRF invalidation and stale-access behavior have named local assertions. |
| U | Profile/contact/address | `UserProfileServiceIntegrationTest`; `UserAddressServiceIntegrationTest`; `UserAddressPostgisIntegrationTest.java:25`; `AuthIdorAcceptanceTest.java:119`; `AuthDatabaseInvariantTest.java:24` | Owned profile/contact/address IDOR/default/partial uniqueness and direct WGS84 X=longitude/Y=latitude assertions retained. Live map/geocoder provider is not established. |
| M | Merchant/capabilities/hours | `MerchantControllerIntegrationTest.java:59`, `:135`; `MerchantLocationControllerIntegrationTest.java:56`, `:79`; `MerchantAccessPolicyIntegrationTest.java:159`, `:225`; `MerchantHoursServiceTest` | Create+OWNER, last owner, STAFF assignment/capability and next-request membership changes are asserted; actual JWT revocation detail retained from backend evidence matrix. No new role/business restriction invented. |
| V | Media/direct upload | `MediaUploadServiceIntegrationTest`; `MediaAttachmentPolicyTest`; `MediaMagicSnifferTest`; `MediaObjectStorageAcceptanceTest`; `CatalogImageSpoofStorageIntegrationTest` | Actual owned MinIO/presigned PUT and image sniff/owner/READY assertions retained. Current Seller upload/publish/guest proof retained by handoff. Production bucket CORS/delivery remains external. |
| K | Catalog/create/edit/lifecycle | `CatalogInvariantTest.java:84`, `:101`, `:167`; `MerchantCatalogCreateControllerIntegrationTest`; `MerchantCatalogEditControllerIntegrationTest.java:489`, `:522`, `:532`; `CatalogLifecycleOutboxRollbackIntegrationTest` | Required attribute/create replay/edit stale/final variant/capability/lifecycle rollback have named assertions. Billing+Social current guards retained; no existing lifecycle rewrite. |
| D | Discovery/location/cursor | `LocationContextResolverTest`; `CatalogDetailServiceIntegrationTest`; `CatalogDiscoveryRepositoryIntegrationTest.java:86`, `:263`, `:294`; `PilotFixtureIntegrationTest.java:225` | Nearest concrete location before price sort, all tied keyset pages, hours exception, location ownership and actual synthetic PG hybrid coverage proof retained in dynamic-contract report. Opt-in pilot evidence is separate from default CI skips. |
| H | Shipping/coverage | `CheckoutDeliveryCoverageIntegrationTest.java:35`; `PilotFixtureIntegrationTest.java:225`; `ShippingPolicySnapshotFactoryTest.java:21`, `:38`; `ShippingPolicyServiceIntegrationTest`; `CheckoutHttpAcceptanceTest.java:379` | PostGIS radius/descendant area and both-enabled hybrid OR, fixed/negotiated immutable snapshots retained. ANY/ALL exact thresholds are not covered by the existing one-ANY-rule snapshot; Q03 stays open. |
| I | Inventory/cart/checkout | `Plan04SchemaMigrationTest.java:48`; `MerchantCatalogEditControllerIntegrationTest.java:658`, `:723`; `CheckoutHttpAcceptanceTest.java:64`, `:134`, `:175`, `:472`, `:525`; `CheckoutLastStockConcurrencyTest.java:139`; `CheckoutRollbackInvariantTest` | Per-item bulk success/failure, ledger/replay/below-reserved/STAFF, real JWT cart/last-unit/one-token concurrency and rollback assertions retained. Foreign/consumed tokens proven; expiry/TTL/hash assertions separately Q01/Q02. |
| B | Order snapshots/state/roles | `OrderSnapshotImmutabilityIntegrationTest`; `OrderReadServiceTest`; `OrderAllowedActionServiceTest`; `OrderCancellationControllerIntegrationTest`; `InventoryReservationOutcomeTest` | Current DTO/private reads, actor actions, delivery/pickup/Service/no-show/issues, immutable order snapshots and reservation/SALE/dispatch assertions retained. A method's source existence is not a new per-branch runtime run. |
| O | Order concurrency/schedulers | `OrderCancellationControllerIntegrationTest.java:294`, `:351`, `:413`, `:432`, `:449`; `performance/OrderCommandSoakConcurrencyTest.java:105`, `:126`, `:151` | All seven originally named Task18 pair identities now have scoped local proof; latest three9PASS/0skip in isolated log, not a new full suite. Selected-order auto-complete worker proof is NOT scheduler due-candidate/grace-clock transport proof. |
| W | Web surfaces/branching | `apps/web/features/auth`; `profile`; `merchant`; `seller`; `catalog`; `shipping`; `inventory`; `cart`; `checkout`; `orders`; `apps/web/tests` | Retain current component/typecheck/client/build gates and documented Cart If-Match/catalog-conflict branch tests. Older201capture/manual ledger is hash-bound historical evidence, not all current P0 manual review; Figma matching is waived. |

## Detailed-plan acceptance statement ledger

Original line anchors below refer to the named plan in each section, not this file. `PASS_LOCAL` always inherits the exact scope/limits in its evidence key; it is not a fresh run claim. The37 old Markdown entries are reconciled here as well as171 formerly omitted fenced statements. Recipe/ordering-only requirements are classified separately rather than reopened. This ledger covers the208explicit end-gate/checklist statements; other prose/type signatures/code examples remain subject to their parent Task and are not all promoted to PASS by this table.

### Plan01 — 31 statements

Original: `docs/CoVie-technical-plan-01-foundation-repository-v1.0.md`.

| Delta ID / original line | Exact original statement | Disposition / evidence key |
|---|---|---|
| P01-D001 / 46 | repo clone -> documented bootstrap path exists | PASS_LOCAL; F |
| P01-D002 / 47 | Docker Compose config is valid | PASS_LOCAL; F |
| P01-D003 / 48 | PostgreSQL/PostGIS, Redis and MinIO run locally | PASS_LOCAL; F |
| P01-D004 / 49 | Spring Boot starts against a clean database | PASS_LOCAL; F |
| P01-D005 / 50 | Flyway V001 enables postgis, pg_trgm and unaccent | PASS_LOCAL; F |
| P01-D006 / 51 | /api/v1/health returns the standard success DTO and X-Request-Id | PASS_LOCAL; R |
| P01-D007 / 52 | /actuator/health is available to internal infrastructure | PASS_LOCAL; R |
| P01-D008 / 53 | malformed/validation test requests use the standard ApiErrorResponse shape | PASS_LOCAL; R |
| P01-D009 / 54 | UUIDv7, Money and AppClock primitives have focused tests | PASS_LOCAL; P |
| P01-D010 / 55 | OpenAPI JSON can be exported from the running/test application | PASS_LOCAL; C |
| P01-D011 / 56 | packages/api-client regenerates TypeScript schema from OpenAPI | PASS_LOCAL; C |
| P01-D012 / 57 | Next.js home renders and typecheck/tests/build pass | PASS_LOCAL; W |
| P01-D013 / 58 | browser dev requests to /api/v1 can be proxied to Spring Boot | PASS_LOCAL; W |
| P01-D014 / 59 | Nginx same-domain routing config passes nginx -t | NOT_VERIFIED; N / Q04: current binary syntax receipt absent |
| P01-D015 / 60 | CI runs backend, frontend and contract baseline checks | PASS_LOCAL; C |
| P01-D016 / 61 | no generated secret or .env.local is tracked | PASS_LOCAL; S |
| P01-D017 / 2620 | Every Master Plan 01 deliverable has a task. | PASS_LOCAL; F |
| P01-D018 / 2621 | No business feature from Plans 02–10 was pulled into Foundation. | HISTORICAL_SUPERSEDED; F; original plan-stage boundary, not current feature removal |
| P01-D019 / 2622 | Flyway and PostgreSQL/PostGIS are exercised by real integration tests. | PASS_LOCAL; F |
| P01-D020 / 2623 | No H2 fallback exists. | PASS_LOCAL; F |
| P01-D021 / 2624 | Standard API error shape and request ID are established before domain controllers. | PASS_LOCAL; F |
| P01-D022 / 2625 | Shared `Money` has no floating-point source field. | PASS_LOCAL; P |
| P01-D023 / 2626 | `CurrentActor` contains only global roles; merchant roles are deferred. | HISTORICAL_SUPERSEDED; P; original plan-stage boundary, not current feature removal |
| P01-D024 / 2627 | `OutboxEventWriter` is a port only, not a fake no-op bean. | HISTORICAL_SUPERSEDED; P; original plan-stage boundary, not current feature removal |
| P01-D025 / 2628 | OpenAPI generation has a deterministic diff gate. | PASS_LOCAL; C |
| P01-D026 / 2629 | Frontend uses generated types rather than handwritten API DTO duplication. | PASS_LOCAL; C |
| P01-D027 / 2630 | Next.js dev proxy does not turn Next.js into the production BFF. | PASS_LOCAL; N |
| P01-D028 / 2631 | Nginx blocks public `/actuator/*` in the routing skeleton. | PASS_LOCAL; N |
| P01-D029 / 2632 | `.env.example` contains examples only; real secret files remain ignored. | PASS_LOCAL; S |
| P01-D030 / 2633 | CI and local verification run the same core commands. | PASS_LOCAL; F |
| P01-D031 / 2634 | Final acceptance uses fresh verification evidence. | PASS_LOCAL; F |

### Plan02 — 51 statements

Original: `docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md`.

| Delta ID / original line | Exact original statement | Disposition / evidence key |
|---|---|---|
| P02-D001 / 3387 | new Google user -> onboarding password -> authenticated | PASS_LOCAL; A |
| P02-D002 / 3388 | no users row exists before onboarding succeeds | PASS_LOCAL; A |
| P02-D003 / 3389 | Google subject is canonical identity key | PASS_LOCAL; A |
| P02-D004 / 3390 | same user can later log in by Google | PASS_LOCAL; A |
| P02-D005 / 3391 | Google authorization initiation and callback both stay under /api/v1 routing | PASS_LOCAL; A |
| P02-D006 / 3392 | same user can later log in by Email/Password | PASS_LOCAL; A |
| P02-D007 / 3393 | unknown email and wrong password share INVALID_CREDENTIALS response | PASS_LOCAL; A |
| P02-D008 / 3394 | Argon2id hashes use versioned prefix and configured parameters | PASS_LOCAL; J; prefix/round-trip test + exact literal source configuration, not new parameter test |
| P02-D009 / 3395 | access JWT carries sub/global roles/sessionId/jti and validates RSA signature | PASS_LOCAL; J |
| P02-D010 / 3396 | access token TTL is 15 minutes | PASS_LOCAL; J |
| P02-D011 / 3397 | initial refresh/session lifetime is 30 days; rotation never extends session expiry | PASS_LOCAL; T |
| P02-D012 / 3398 | refresh rotates every time | PASS_LOCAL; T |
| P02-D013 / 3399 | reuse revokes whole auth session family | PASS_LOCAL; T |
| P02-D014 / 3400 | multi-device sessions remain independent | PASS_LOCAL; T |
| P02-D015 / 3401 | revoked session JWT fails on next authenticated request | PASS_LOCAL; T |
| P02-D016 / 3402 | non-ACTIVE user account fails on next authenticated request even if JWT/session were previously valid | PASS_LOCAL; T |
| P02-D017 / 3403 | Google password recovery revokes old sessions and creates one new session | PASS_LOCAL; A |
| P02-D018 / 3404 | browser unsafe mutation requires CSRF | PASS_LOCAL; T |
| P02-D019 / 3405 | CSRF token is actively invalidated across authentication/logout boundaries and reacquired before later unsafe calls | PASS_LOCAL; T |
| P02-D020 / 3406 | stale access cookie does not block login/refresh/logout/recovery under /api/v1/auth/** | PASS_LOCAL; T |
| P02-D021 / 3407 | Bearer-header mutation is CSRF-exempt | PASS_LOCAL; T |
| P02-D022 / 3408 | /me resolves only current user | PASS_LOCAL; U |
| P02-D023 / 3409 | other user's session/address/contact IDs return 404 | PASS_LOCAL; U |
| P02-D024 / 3410 | saved address round-trips longitude/latitude correctly through PostGIS geography | PASS_LOCAL; U |
| P02-D025 / 3411 | one default active address invariant holds | PASS_LOCAL; U |
| P02-D026 / 3412 | OpenAPI is regenerated and typed client compiles | PASS_LOCAL; C |
| P02-D027 / 3413 | web login/onboarding/reset pages compile and tests pass | PASS_LOCAL; W |
| P02-D028 / 3414 | no raw password/access/refresh/action/CSRF secret appears in audit/log tests | PASS_LOCAL; S |
| P02-D029 / 3415 | no real OAuth or JWT private secret is tracked | PASS_LOCAL; S |
| P02-D030 / 3599 | Plan 01 interfaces are reused rather than duplicated. | PASS_LOCAL; T |
| P02-D031 / 3600 | Migration numbering no longer jumps to V014 before later domain migrations. | HISTORICAL_SUPERSEDED; F; original plan-stage boundary, not current feature removal |
| P02-D032 / 3601 | `users` contains no email/password fields. | PASS_LOCAL; A |
| P02-D033 / 3602 | Google `sub` is the identity key. | PASS_LOCAL; A |
| P02-D034 / 3603 | New Google callback alone cannot create a fully authenticated user. | PASS_LOCAL; A |
| P02-D035 / 3604 | Onboarding/recovery raw ticket is never placed in URL or DB. | PASS_LOCAL; A |
| P02-D036 / 3605 | Local EMAIL uniqueness does not incorrectly block a GOOGLE row carrying the same email metadata. | PASS_LOCAL; A |
| P02-D037 / 3606 | Password hash has algorithm/version prefix. | PASS_LOCAL; A |
| P02-D038 / 3607 | No plaintext password/token is logged/audited. | PASS_LOCAL; S |
| P02-D039 / 3608 | JWT global roles contain only USER/ADMIN. | PASS_LOCAL; J |
| P02-D040 / 3609 | Merchant roles remain deferred to Plan 03 DB membership checks. | PASS_LOCAL; J |
| P02-D041 / 3610 | Refresh/revoke/password-reset mutations use the same session-first, token-second lock order. | PASS_LOCAL; T |
| P02-D042 / 3611 | Strict reuse detection has a real concurrent PostgreSQL test. | PASS_LOCAL; F |
| P02-D043 / 3612 | Logout and session revoke invalidate future requests through active-session validation. | PASS_LOCAL; S |
| P02-D044 / 3613 | CSRF is enabled for browser unsafe requests. | PASS_LOCAL; T |
| P02-D045 / 3614 | Bearer-header clients do not need browser CSRF. | PASS_LOCAL; T |
| P02-D046 / 3615 | `/me` and child resources derive user ID from `CurrentActor`. | PASS_LOCAL; P |
| P02-D047 / 3616 | IDOR returns 404 for another user's private IDs. | PASS_LOCAL; U |
| P02-D048 / 3617 | PostGIS address test catches lat/lng reversal. | PASS_LOCAL; U |
| P02-D049 / 3618 | `location_area_id`/`avatar_media_id` FKs are intentionally deferred until Plan 03 tables exist. | HISTORICAL_SUPERSEDED; F; original plan-stage boundary, not current feature removal |
| P02-D050 / 3619 | OpenAPI/client generation remains deterministic. | PASS_LOCAL; C |
| P02-D051 / 3620 | Plan 02 contains no merchant/catalog/order business implementation. | HISTORICAL_SUPERSEDED; S; original plan-stage boundary, not current feature removal |

### Plan03 — 31 statements

Original: `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md`.

| Delta ID / original line | Exact original statement | Disposition / evidence key |
|---|---|---|
| P03-D001 / 3653 | Flyway clean DB applies V001 -> V006 in order | PASS_LOCAL; F |
| P03-D002 / 3654 | location_areas exists and PostGIS GIST indexes are active | PASS_LOCAL; F |
| P03-D003 / 3655 | user_addresses.location_area_id FK is installed | PASS_LOCAL; F |
| P03-D004 / 3656 | media upload uses presigned direct PUT, not Spring binary proxy | PASS_LOCAL; V |
| P03-D005 / 3657 | JPEG/PNG/WebP magic mismatch is rejected | PASS_LOCAL; V |
| P03-D006 / 3658 | media wrong owner/PENDING/FAILED cannot be attached | PASS_LOCAL; V |
| P03-D007 / 3659 | merchant creation atomically creates OWNER membership | PASS_LOCAL; M |
| P03-D008 / 3660 | last active OWNER cannot be removed | PASS_LOCAL; M |
| P03-D009 / 3661 | membership revoke changes authorization on next request | PASS_LOCAL; M |
| P03-D010 / 3662 | STAFF can access only explicitly assigned MerchantLocation | PASS_LOCAL; M |
| P03-D011 / 3663 | MerchantLocation coordinates are lng/lat-correct and timezone validated | PASS_LOCAL; M |
| P03-D012 / 3664 | hours exception overrides weekly schedule | PASS_LOCAL; D |
| P03-D013 / 3665 | every CatalogItem has >=1 Variant | PASS_LOCAL; K |
| P03-D014 / 3666 | Product/Service LocationVariant inventory-mode invariant holds | PASS_LOCAL; K |
| P03-D015 / 3667 | price source is LocationVariant only | PASS_LOCAL; K |
| P03-D016 / 3668 | aggregate catalog create is idempotent | PASS_LOCAL; K |
| P03-D017 / 3669 | same idempotency key with different body returns 409 | PASS_LOCAL; K |
| P03-D018 / 3670 | mutable seller resources honor ETag / If-Match | PASS_LOCAL; K |
| P03-D019 / 3671 | stale concurrent update returns RESOURCE_VERSION_CONFLICT | PASS_LOCAL; K |
| P03-D020 / 3672 | publish/pause/activate/archive cannot be done through arbitrary status PATCH | PASS_LOCAL; K |
| P03-D021 / 3673 | catalog lifecycle writes Outbox intent in same transaction | PASS_LOCAL; K |
| P03-D022 / 3674 | public hidden/inactive resources return 404 | PASS_LOCAL; K |
| P03-D023 / 3675 | search requires explicit LocationContext | PASS_LOCAL; D |
| P03-D024 / 3676 | another user's addressId returns 404 | PASS_LOCAL; D |
| P03-D025 / 3677 | one discovery result resolves one concrete nearest MerchantLocation | PASS_LOCAL; D |
| P03-D026 / 3678 | public variant offer exposes exact locationVariantId | PASS_LOCAL; D |
| P03-D027 / 3679 | price sort does not silently resolve to a farther merchant location | PASS_LOCAL; D |
| P03-D028 / 3680 | cursor is opaque and keyset pagination has no duplicates on tie values | PASS_LOCAL; D |
| P03-D029 / 3681 | generated OpenAPI/TypeScript client is in sync | PASS_LOCAL; C |
| P03-D030 / 3682 | backend clean verify passes | PASS_LOCAL; C |
| P03-D031 / 3683 | frontend lint/typecheck/test/build passes | PASS_LOCAL; W |

### Plan04 — 44 statements

Original: `docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md`.

| Delta ID / original line | Exact original statement | Disposition / evidence key |
|---|---|---|
| P04-D001 / 2866 | Flyway clean DB applies V001 -> V009 in order | PASS_LOCAL; F |
| P04-D002 / 2867 | one active PRODUCT_DELIVERY coverage per location/type | PASS_LOCAL; H |
| P04-D003 / 2868 | one active SERVICE_AREA coverage per location/type | PASS_LOCAL; H |
| P04-D004 / 2869 | coverage radius uses PostGIS geography | PASS_LOCAL; H |
| P04-D005 / 2870 | selected area matches descendants correctly | PASS_LOCAL; H |
| P04-D006 / 2871 | coverage modes combine with OR | PASS_LOCAL; H |
| P04-D007 / 2872 | shipping FREE/FIXED/NEGOTIATED/CONDITIONAL semantics pass tests | PASS_LOCAL; H |
| P04-D008 / 2873 | NEGOTIATED shipping fee and total remain null | PASS_LOCAL; H |
| P04-D009 / 2874 | conditional ANY/ALL boundaries are tested | NOT_VERIFIED; H / Q03 |
| P04-D010 / 2875 | every existing/new TRACKED Product offer has one inventory balance | PASS_LOCAL; I |
| P04-D011 / 2876 | Service never gets inventory balance | PASS_LOCAL; I |
| P04-D012 / 2877 | TRACKED -> UNTRACKED cannot discard reserved or physical stock silently | PASS_LOCAL; I |
| P04-D013 / 2878 | set-stock always writes adjustment ledger when delta != 0 | PASS_LOCAL; I |
| P04-D014 / 2879 | stock cannot fall below reservedQuantity | PASS_LOCAL; I |
| P04-D015 / 2880 | bulk stock returns per-item independent results | PASS_LOCAL; I |
| P04-D016 / 2881 | one active Cart exists per user/location/type | PASS_LOCAL; I |
| P04-D017 / 2882 | same Merchant different location gets separate Cart | PASS_LOCAL; I |
| P04-D018 / 2883 | Product/Service never share Cart | PASS_LOCAL; I |
| P04-D019 / 2884 | add-to-cart uses exact locationVariantId | PASS_LOCAL; I |
| P04-D020 / 2885 | Service cart quantity is exactly 1 | PASS_LOCAL; I |
| P04-D021 / 2886 | Cart current view uses latest price and warns on change | PASS_LOCAL; I |
| P04-D022 / 2887 | Cart mutations enforce If-Match | PASS_LOCAL; I |
| P04-D023 / 2888 | Preview supports CART and DIRECT through common CheckoutCandidate | PASS_LOCAL; I |
| P04-D024 / 2889 | Preview never reserves stock | PASS_LOCAL; I |
| P04-D025 / 2890 | Preview never locks price | PASS_LOCAL; I |
| P04-D026 / 2891 | checkout token is opaque, hashed at rest, 15-minute TTL and single-use | NOT_VERIFIED; I / Q02 (single-use part PASS_LOCAL retained) |
| P04-D027 / 2892 | expired token is rejected | NOT_VERIFIED; I / Q01 |
| P04-D028 / 2893 | consumed token with new idempotency key cannot create another Order | PASS_LOCAL; I |
| P04-D029 / 2894 | Commit revalidates item/price/location/coverage/minimum/shipping/inventory | PASS_LOCAL; I |
| P04-D030 / 2895 | changed authoritative Preview data returns 409 CHECKOUT_CHANGED | PASS_LOCAL; I |
| P04-D031 / 2896 | Commit idempotency retry creates exactly one Order | PASS_LOCAL; I |
| P04-D032 / 2897 | Commit creates real PENDING_CONFIRMATION Order + OrderItems + fulfillment + ORDER_CREATED event | PASS_LOCAL; I |
| P04-D033 / 2898 | Order stores immutable pricing/location/address/shipping snapshots | PASS_LOCAL; B |
| P04-D034 / 2899 | tracked stock reservation is created at Order creation | PASS_LOCAL; I |
| P04-D035 / 2900 | untracked Product and Service create no reservation | PASS_LOCAL; I |
| P04-D036 / 2901 | source Cart selected items are cleared only after successful transaction | PASS_LOCAL; I |
| P04-D037 / 2902 | Order creation and Outbox intent are atomic | PASS_LOCAL; I |
| P04-D038 / 2903 | rollback injection leaves no partial Order/reservation/movement/cart mutation | PASS_LOCAL; I |
| P04-D039 / 2904 | last-stock concurrency test yields exactly one success + one conflict | PASS_LOCAL; I |
| P04-D040 / 2905 | reverse item ordering concurrency test does not deadlock | PASS_LOCAL; I |
| P04-D041 / 2906 | public catalog fulfillment filters preserve resolved-location correctness | PASS_LOCAL; D |
| P04-D042 / 2907 | generated OpenAPI/TypeScript client is in sync | PASS_LOCAL; C |
| P04-D043 / 2908 | backend clean verify passes | PASS_LOCAL; C |
| P04-D044 / 2909 | frontend lint/typecheck/test/build passes | PASS_LOCAL; W |

### Plan05 — 51 statements

Original: `docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md`.

| Delta ID / original line | Exact original statement | Disposition / evidence key |
|---|---|---|
| P05-D001 / 2830 | Canonical Order detail/list                            -> Tasks 4, 6, 7, 17 | PASS_LOCAL; B |
| P05-D002 / 2831 | 404 vs 403 private resource rules                      -> Tasks 4, 17 | PASS_LOCAL; B |
| P05-D003 / 2832 | allowedActions                                         -> Task 6 | PASS_LOCAL; B |
| P05-D004 / 2833 | explicit commands / no PATCH status                    -> Tasks 10–17 | PASS_LOCAL; B |
| P05-D005 / 2834 | seller confirm/reject                                  -> Task 10 | PASS_LOCAL; B |
| P05-D006 / 2835 | pre-confirm buyer cancel                               -> Task 10 | PASS_LOCAL; B |
| P05-D007 / 2836 | post-confirm cancellation request                      -> Task 11 | PASS_LOCAL; B |
| P05-D008 / 2837 | seller accept/reject cancellation                      -> Task 11 | PASS_LOCAL; B |
| P05-D009 / 2838 | seller post-confirm cancel                             -> Task 11 | PASS_LOCAL; B |
| P05-D010 / 2839 | Product seller delivery                                -> Task 12 | PASS_LOCAL; B |
| P05-D011 / 2840 | Product pickup                                         -> Task 13 | PASS_LOCAL; B |
| P05-D012 / 2841 | buyer no-show/contest                                  -> Task 13 | PASS_LOCAL; B |
| P05-D013 / 2842 | Service performed/completed                            -> Task 14 | PASS_LOCAL; B |
| P05-D014 / 2843 | buyer direct completion from CONFIRMED                 -> Tasks 12, 14 | PASS_LOCAL; B |
| P05-D015 / 2844 | Delivery/Service issues                                -> Task 15 | PASS_LOCAL; B |
| P05-D016 / 2845 | auto-completion                                        -> Task 16 | PASS_LOCAL; O |
| P05-D017 / 2846 | pending expiration                                     -> Task 16 | PASS_LOCAL; O |
| P05-D018 / 2847 | issue timeout                                          -> Task 16 | PASS_LOCAL; O |
| P05-D019 / 2848 | inventory release/commit                               -> Tasks 8–16 | PASS_LOCAL; B |
| P05-D020 / 2849 | append-only Order events                               -> Task 5 | PASS_LOCAL; B |
| P05-D021 / 2850 | transactional outbox intent                            -> Tasks 5, 9–16 | PASS_LOCAL; B |
| P05-D022 / 2851 | idempotency                                            -> Task 17 | PASS_LOCAL; B |
| P05-D023 / 2852 | concurrency/race tests                                 -> Task 18 | PASS_LOCAL; O; original seven named pairs only, latest three isolated9PASS; not all conceivable pair/state products |
| P05-D024 / 2853 | OpenAPI/generated client                               -> Task 19 | PASS_LOCAL; C |
| P05-D025 / 2854 | minimal buyer/seller UI                                -> Task 19 | PASS_LOCAL; W |
| P05-D026 / 2654 | buyer reads only own private Orders | PASS_LOCAL; B |
| P05-D027 / 2655 | seller sees only merchant/location Orders they may access | PASS_LOCAL; B |
| P05-D028 / 2656 | STAFF location restriction works | PASS_LOCAL; B |
| P05-D029 / 2657 | canonical Order detail returns actor-specific allowedActions | PASS_LOCAL; B |
| P05-D030 / 2658 | seller/provider confirms/rejects pending Order | PASS_LOCAL; B |
| P05-D031 / 2659 | buyer cancels pending freely | PASS_LOCAL; B |
| P05-D032 / 2660 | buyer post-confirm cancellation requires seller decision | PASS_LOCAL; W |
| P05-D033 / 2661 | cancellation rejection returns exact previous operational status | PASS_LOCAL; B |
| P05-D034 / 2662 | seller post-confirm cancellation requires reason and releases reservation | PASS_LOCAL; W |
| P05-D035 / 2663 | seller-delivery progress works | PASS_LOCAL; B |
| P05-D036 / 2664 | pickup/no-show grace enforced by server time | PASS_LOCAL; B |
| P05-D037 / 2665 | buyer can contest no-show | PASS_LOCAL; B |
| P05-D038 / 2666 | service performed flow works | PASS_LOCAL; B |
| P05-D039 / 2667 | buyer can complete directly from CONFIRMED | PASS_LOCAL; B |
| P05-D040 / 2668 | issue only allowed after declared fulfillment | PASS_LOCAL; B |
| P05-D041 / 2669 | issue blocks auto-complete | PASS_LOCAL; O |
| P05-D042 / 2670 | pending timeout expires Order and releases inventory | PASS_LOCAL; O |
| P05-D043 / 2671 | normal completion commits tracked reservation + SALE | PASS_LOCAL; B |
| P05-D044 / 2672 | no-show releases reservation | PASS_LOCAL; B |
| P05-D045 / 2673 | no-show-contested late completion never recommits RELEASED inventory | PASS_LOCAL; B |
| P05-D046 / 2674 | delivery CLOSED_WITH_ISSUE commits ACTIVE stock instead of restocking; no-show contest remains RELEASED | PASS_LOCAL; B |
| P05-D047 / 2675 | COMPLETED is terminal | PASS_LOCAL; B |
| P05-D048 / 2676 | all important transitions append OrderEvent + outbox intent atomically | PASS_LOCAL; B |
| P05-D049 / 2677 | Idempotency-Key retry does not duplicate event/inventory/outbox | PASS_LOCAL; B |
| P05-D050 / 2678 | concurrent commands resolve to one valid state | PASS_LOCAL; O; original seven named pairs only, latest three isolated9PASS; not all conceivable pair/state products |
| P05-D051 / 2679 | no admin force-status endpoint exists | PASS_LOCAL; B |

## Master decomposition and feature DoD ledger

Original `docs/CoVie-technical-plan-v1.0.md` sections30,41,42;69exact deliverables/gates/DoD statements below. For universal feature DoD, named evidence is not a system-wide syntactic/security proof. Do not reinterpret a future unreviewed feature as accepted from these mappings.

| Master ID / original line | Exact statement | Mapping / disposition and limits |
|---|---|---|
| M-P01-01 / 1170 | monorepo bootstrapped | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-02 / 1171 | Next.js boots | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-03 / 1172 | Spring Boot boots | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-04 / 1173 | PostgreSQL/PostGIS/Redis/MinIO local infra | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-05 / 1174 | Flyway foundation | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-06 / 1175 | request-id/error DTO baseline | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-07 / 1176 | OpenAPI generation | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-08 / 1177 | CI baseline | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P01-09 / 1180 | Gate: `/api/v1/health`/Actuator internal health + web home render + CI green. | PASS_LOCAL; F/P/R/C/W/N. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. Nginx binary Q04 remains separately open. |
| M-P02-01 / 1189 | users/user identities/addresses | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-02 / 1190 | Google first-registration onboarding | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. Live Google remains external; local fixture path retained. |
| M-P02-03 / 1191 | set-password | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-04 / 1192 | email/password login | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-05 / 1193 | access JWT | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-06 / 1194 | rotating refresh tokens | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-07 / 1195 | session management | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-08 / 1196 | Google password recovery | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. Live Google remains external; local fixture path retained. |
| M-P02-09 / 1197 | CSRF | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-10 / 1198 | CurrentActor | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P02-11 / 1201 | Gate: complete authentication testing contract passes. | PASS_LOCAL; A/J/T/U/C/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-01 / 1210 | MerchantProfile/members/location authorization | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-02 / 1211 | location hours/contact | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-03 / 1212 | media presigned upload | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-04 / 1213 | categories/catalog/options/variants/location offers | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-05 / 1214 | publish/pause lifecycle | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-06 / 1215 | public catalog detail/search | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-07 / 1216 | PostGIS LocationContext | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P03-08 / 1219 | Gate: guest can discover a purchasable `LocationVariant`; seller can manage it. | PASS_LOCAL; M/V/K/D/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-01 / 1228 | delivery coverage | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-02 / 1229 | shipping policies | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-03 / 1230 | multi-cart | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-04 / 1231 | tracked/untracked inventory | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-05 / 1232 | checkout Preview/Commit | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-06 / 1233 | checkout_sessions | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. Q01/Q02 remain unverified named assertions. |
| M-P04-07 / 1234 | idempotency | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-08 / 1235 | inventory concurrency | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P04-09 / 1238 | Gate: buyer can create one `PENDING_CONFIRMATION` Order and concurrent last-stock test yields 1 success + 1 conflict. | PASS_LOCAL; H/I/B/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-01 / 1247 | canonical order read model | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-02 / 1248 | allowedActions | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-03 / 1249 | seller confirm/reject | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-04 / 1250 | buyer/seller cancellation | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-05 / 1251 | seller delivery | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-06 / 1252 | pickup/no-show | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-07 / 1253 | service fulfillment | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-08 / 1254 | issues | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-09 / 1255 | auto-complete/expiry | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-10 / 1256 | order events | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-11 / 1257 | reservation release/commit | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-P05-12 / 1260 | Gate: Product delivery, Pickup và Service happy paths + critical forbidden transitions pass. | PASS_LOCAL; B/O/W. Mapped to detailed-plan/evidence keys above; original gate is local scope, not deployment. |
| M-DoD41-01 / 1690 | API contract đúng design | PASS_LOCAL; C. Existing domain-specific OpenAPI/HTTP assertions; not future endpoint review. |
| M-DoD41-02 / 1691 | business rules nằm application/domain layer | PASS_LOCAL; B/K/I/A. Application/domain services mapped; controller not authoritative business logic. |
| M-DoD41-03 / 1692 | JPA entity không leak | NOT_VERIFIED; C/B/U. DTOs used on named paths; no exhaustive every-response JPA-leak assertion audited here. |
| M-DoD41-04 / 1693 | migration/constraint/index cần thiết đã có | PASS_LOCAL; F. Schema/constraint/index and dual-lineage proofs retained. |
| M-DoD41-05 / 1694 | unit/integration tests pass | PASS_LOCAL; C. Source-bound retained configured tests; local changed surfaces have separate targeted receipts, not new full current-tree run. |
| M-DoD41-06 / 1695 | negative authz tests có | PASS_LOCAL; T/M/K/I/B. Named authz/IDOR/STAFF tests, not every unenumerated endpoint. |
| M-DoD41-07 / 1696 | idempotency/concurrency test có nếu feature cần | PASS_LOCAL; I/O/K/T. Named idempotency/PG race requirements; no every conceivable race claim. |
| M-DoD41-08 / 1697 | outbox side effect atomic nếu feature phát event | PASS_LOCAL; B/K/I. Named rollback/order/lifecycle event/outbox atomicity. |
| M-DoD41-09 / 1698 | OpenAPI updated | PASS_LOCAL; C. Combined canonical schema retained. |
| M-DoD41-10 / 1699 | generated client updated | PASS_LOCAL; C. Generated client/drift gates retained. |
| M-DoD41-11 / 1700 | structured logs không leak sensitive data | NOT_VERIFIED; S. Auth distinctive-secret logging tests retained; no exhaustive every-domain-log safety assertion. Need newly found logger is separately tested, not whole log universe. |
| M-DoD42-01 / 1708 | uses generated API types/client | PASS_LOCAL; C/W. Generated client and named feature callers. |
| M-DoD42-02 / 1709 | loading/empty/error states implemented | NOT_VERIFIED; W. Named components cover branching; exhaustive current screen/state inventory is W08/Plan09, not implied. |
| M-DoD42-03 / 1710 | server error codes mapped safely | PASS_LOCAL; W/R. Named Cart If-Match/catalog conflict/client safe-error tests; preserve unknown fallback. |
| M-DoD42-04 / 1711 | no duplicated authoritative business calculation | PASS_LOCAL; I/B/W. Authoritative pricing/snapshots from BE; UI tests retain nullable negotiated totals. |
| M-DoD42-05 / 1712 | responsive mobile-first behavior | NOT_VERIFIED; W. Current responsive/manual proof for all upgraded screens not inferred from historical201captures. |
| M-DoD42-06 / 1713 | critical mutation handles 409/422/429 | NOT_VERIFIED; W/R. Named mutation/conflict tests exist; every critical command409/422/429 current UI combination not separately reconciled. |
| M-DoD42-07 / 1714 | SSE only triggers REST refetch | PASS_LOCAL; W. Event/refetch architecture is Plan07/09 scope; retained source pattern, no deployed EventSource claim. |
| M-DoD42-08 / 1715 | component tests for branching UI | PASS_LOCAL; W. Current named component branching tests retained. |
| M-DoD42-09 / 1716 | Playwright coverage for P0 journey | NOT_VERIFIED; W. Current all-P0 normal-auth/live-provider browser coverage not established by base CI or prior old hash-bound captures. |

## Current bounded conclusion

-208explicit detailed-plan statements are dispositioned: 198PASS_LOCAL, 4NOT_VERIFIED, 6HISTORICAL_SUPERSEDED.69master statements are mapped: 63PASS_LOCAL, 6NOT_VERIFIED. These counts describe this ledger, not total defect count or all normative prose.
- Three newly verified original Order race pairs remain closed locally. No current source defect found by this delta. Four narrow evidence rows Q01–Q04 remain, with Q01/Q02 sharing one checkout-session fixture; none warrants changing production logic without a failing required assertion.
- Keep exact baseline/local-delta provenance. Candidate packages were built before the new race test source; production classes are not changed by those race additions. Existing full CI does not automatically cover unpublished Need/Docker changes or current flags.
- Deployment HTTPS/OAuth/provider/storage, off-host restore, field/real-device observations and live bank settlement remain external/owner-operated acceptance. They are not additional speculative backend implementations. Staging waiver permits local work, not invented deployed evidence.
- This audit itself ran read-only source searches and read existing receipts only. It did not run Maven, change existing documents/apps/tests, access credentials, invoke providers, create/pay invoices, alter flags, write a database or perform Git mutations.
