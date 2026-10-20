# Project acceptance — Plan01–05 source census

Baseline: main `5ad071ffb9f3c2f9d7cfaf327c09726690dc3c1a`. This file is an exact requirement-heading index and bounded evidence reconciliation, not an instruction to rerun every historical TDD/setup/commit step.

## Meaning and limitations

- Module-level local closure is retained from named reports and current source. A module report or full-suite success is not proof that every original child Step assertion was separately executed.
- Each Task/Step below is explicitly `NOT_VERIFIED_INDIVIDUALLY` unless supported by the bounded evidence ledger. This is an evidence qualification, NOT a claim that existing functionality is missing or a requirement to implement hundreds of historical instructions again.
- Original install/commit/bootstrap steps are historical implementation directions. The current integration supersedes old branch/dependency/migration sequencing; do not recreate branches or rewrite SQL to mimic those steps.
- New business requirements must not be inferred from product-roadmap prose. Approved MVP requirements are governed by the original technical plans and later approved Social/Billing extensions.
- Live Google/production HTTPS/private storage, released artifacts and off-host recovery remain separate external proof. Staging is not a prerequisite for continuing local work; Figma matching is owner-waived.

## Retained evidence and concrete gap candidates

| Scope | Disposition | Evidence / limits |
|---|---|---|
| Plan01 foundation | PASS_LOCAL retained at module scope | Historical closure audit plus current repository/CI/toolchain and actual integrated migration lineage; not a new production artifact proof |
| Plan02 auth | PASS_LOCAL retained at named assertions | backend-evidence matrix; existing issued-session/CSRF/cross-user tests and recorded SSR/Redis opt-ins. No live Google/production cookie claim |
| Plan03 merchant/catalog/media | PASS_LOCAL retained at named assertions | Actual current create/edit/privacy/staff/revocation/category HTTP evidence in backend progress, Social local video/upload/range/moderation closure; packaging source binding follows newer handoff |
| Plan04 trading | PASS_LOCAL retained at named assertions | Existing actual checkout/inventory/last-stock and fixed/negotiated pickup/delivery chains; old test counts are not summed into a new full run |
| Plan05 lifecycle | PASS_LOCAL retained at named assertions | Existing actual complete-vs-cancel, complete-vs-delivered, request accept/reject and scheduler/issue cases; no proof of every actor pair follows |
| Plan05 Task18 exact remaining race evidence | PASS_LOCAL for three named pairs,2026-10-20 | Added only confirm-vs-pending-cancel, confirm-vs-reject, buyer-complete-vs-auto-complete after existing source search.9PASS/0skip,3repeats each, both actors observed lock-waiting; JWT HTTP and actual selected-order scheduler worker. See `project-remaining-work-progress-2026-10-19.md`. No Order product fix or blanket scheduler due-selection proof |
| Protected data/current rollout | EXTERNAL_INPUT / instruction boundary | Latest AGENTS requires money/enforcement flagsOFF and no recovered-DB writes. Earlier live settings authorizations are not used to override the newer instruction |

## Plan01: foundation

Original: `docs/CoVie-technical-plan-01-foundation-repository-v1.0.md`.

Source areas: `package.json; infra/compose/compose.local.yml; apps/api/src/main/java/vn/covie/shared/; packages/api-client/; apps/web/app/`.

Evidence to retain: `docs/CoVie-plan-closure-audit-2026-08-24.md; docs/operations/main-handoff-2026-10-19.md`. Read the individual method/recorded result before changing any row into a new PASS claim.

### Task identities

| ID | Exact original Task | Source line | Individual assertion status |
|---|---|---|---|
| P01-T01 | Bootstrap the Monorepo and Toolchain Contract | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:168 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T02 | Add Local PostgreSQL/PostGIS, Redis and MinIO Infrastructure | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:333 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T03 | Bootstrap the Spring Boot API Application | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:455 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T04 | Add Flyway Foundation Migration and Real PostgreSQL Test Harness | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:728 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T05 | Implement UUIDv7, AppClock and Money Shared Primitives | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:884 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T06 | Add Request Correlation, Standard Errors and Health API | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1116 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T07 | Add Shared Security and Outbox Ports Without Implementing Auth or Workers | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1483 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T08 | Export OpenAPI and Build the Typed API Client Package | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1644 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T09 | Bootstrap Next.js Web App and Same-Origin API Wrapper | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1847 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T10 | Add Same-Domain Nginx Routing Skeleton and Developer Bootstrap Documentation | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2156 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T11 | Add CI Baseline | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2289 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P01-T12 | Foundation Acceptance Verification | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2380 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |

### Child Step identities — historical directions, not new bugs

| ID | Exact original Step | Source line |
|---|---|---|
| P01-T01-S01 | Create the root workspace manifest | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:181 |
| P01-T01-S02 | Create the pnpm workspace file | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:206 |
| P01-T01-S03 | Create `.editorconfig` | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:214 |
| P01-T01-S04 | Create `.gitignore` | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:234 |
| P01-T01-S05 | Create `.env.example` as a shape contract, not a production secret file | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:266 |
| P01-T01-S06 | Create the shared TypeScript strict baseline | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:290 |
| P01-T01-S07 | Verify workspace parsing before any app exists | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:305 |
| P01-T01-S08 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:324 |
| P01-T02-S01 | Write Compose configuration with explicit service names and local-only credentials | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:340 |
| P01-T02-S02 | Validate Compose syntax before starting anything | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:402 |
| P01-T02-S03 | Start infrastructure | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:412 |
| P01-T02-S04 | Verify PostgreSQL and Redis from inside containers | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:430 |
| P01-T02-S05 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:446 |
| P01-T03-S01 | Write the failing application-context test first | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:471 |
| P01-T03-S02 | Run the focused test and observe failure because the Maven project/app does not exist yet | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:490 |
| P01-T03-S03 | Create `pom.xml` | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:501 |
| P01-T03-S04 | Generate and commit the Maven Wrapper | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:604 |
| P01-T03-S05 | Add the Spring Boot entrypoint | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:615 |
| P01-T03-S06 | Add base configuration | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:632 |
| P01-T03-S07 | Run the focused test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:708 |
| P01-T03-S08 | Commit the backend skeleton | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:719 |
| P01-T04-S01 | Write the failing migration test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:738 |
| P01-T04-S02 | Add the reusable PostGIS integration-test base | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:767 |
| P01-T04-S03 | Run the migration test before the migration exists | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:804 |
| P01-T04-S04 | Create `V001__extensions_and_foundation.sql` | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:815 |
| P01-T04-S05 | Add a full application-context test using the same real PostgreSQL harness | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:825 |
| P01-T04-S06 | Run migration and context tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:841 |
| P01-T04-S07 | Verify Flyway history explicitly | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:852 |
| P01-T04-S08 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:875 |
| P01-T05-S01 | Write failing UUIDv7 tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:897 |
| P01-T05-S02 | Run and confirm UUID tests fail | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:929 |
| P01-T05-S03 | Implement UUIDv7 using JDK primitives | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:938 |
| P01-T05-S04 | Run UUID tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:972 |
| P01-T05-S05 | Write failing AppClock test and interface contract | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:980 |
| P01-T05-S06 | Write failing Money tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1050 |
| P01-T05-S07 | Implement Money without floating-point arithmetic | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1078 |
| P01-T05-S08 | Run all primitive tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1099 |
| P01-T05-S09 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1107 |
| P01-T06-S01 | Write the failing health/request-id integration test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1135 |
| P01-T06-S02 | Run and confirm failure | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1182 |
| P01-T06-S03 | Create `ErrorCode` and `ApiException` foundation | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1191 |
| P01-T06-S04 | Create the standard error response DTO | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1235 |
| P01-T06-S05 | Implement `RequestIdFilter` | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1265 |
| P01-T06-S06 | Implement health endpoint | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1311 |
| P01-T06-S07 | Add global exception mapping | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1338 |
| P01-T06-S08 | Add a test-only controller for error contract tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1416 |
| P01-T06-S09 | Run API foundation tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1466 |
| P01-T06-S10 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1474 |
| P01-T07-S01 | Write the CurrentActor test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1495 |
| P01-T07-S02 | Implement the security primitives | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1525 |
| P01-T07-S03 | Write the outbox intent test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1556 |
| P01-T07-S04 | Implement the outbox port only | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1589 |
| P01-T07-S05 | Run focused tests | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1626 |
| P01-T07-S06 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1635 |
| P01-T08-S01 | Write an OpenAPI export integration test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1659 |
| P01-T08-S02 | Run export test into Maven target first | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1701 |
| P01-T08-S03 | Create API client package | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1710 |
| P01-T08-S04 | Add the typed client wrapper | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1753 |
| P01-T08-S05 | Add deterministic export script | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1771 |
| P01-T08-S06 | Export schema and generate TypeScript | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1795 |
| P01-T08-S07 | Add generated-drift verification script | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1813 |
| P01-T08-S08 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1838 |
| P01-T09-S01 | Write the failing home component test | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1870 |
| P01-T09-S02 | Create web package manifest | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1883 |
| P01-T09-S03 | Add strict TypeScript config | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1921 |
| P01-T09-S04 | Add dev proxy while preserving production same-origin routing | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1948 |
| P01-T09-S05 | Add environment parsers | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:1975 |
| P01-T09-S06 | Add API client factory | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2007 |
| P01-T09-S07 | Add minimal layout and page | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2041 |
| P01-T09-S08 | Configure Vitest and ESLint | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2102 |
| P01-T09-S09 | Install and run frontend checks | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2135 |
| P01-T09-S10 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2147 |
| P01-T10-S01 | Create Nginx config skeleton | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2164 |
| P01-T10-S02 | Validate Nginx syntax in an isolated container | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2212 |
| P01-T10-S03 | Replace the minimal README with deterministic bootstrap instructions | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2234 |
| P01-T10-S04 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2280 |
| P01-T11-S01 | Create CI workflow | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2296 |
| P01-T11-S02 | Run the same commands locally before relying on CI | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2357 |
| P01-T11-S03 | Commit | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2371 |
| P01-T12-S01 | Verify repository state and secret hygiene | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2387 |
| P01-T12-S02 | Verify clean local infrastructure | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2405 |
| P01-T12-S03 | Verify a clean database migration | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2417 |
| P01-T12-S04 | Run full backend suite | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2428 |
| P01-T12-S05 | Verify OpenAPI and generated client are current | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2438 |
| P01-T12-S06 | Run full frontend suite | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2447 |
| P01-T12-S07 | Start API and web for smoke verification | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2458 |
| P01-T12-S08 | Verify Nginx config again | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2491 |
| P01-T12-S09 | Verify no Plan 01 scope leakage | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2503 |
| P01-T12-S10 | Record the Plan 01 completion commit only after all fresh verification is green | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2519 |

Child status: NOT_VERIFIED_INDIVIDUALLY; existing module/test evidence is retained without implying each procedural step independently passed. No test rerun or source change is authorized solely by these index entries.

### Explicit checklist/gate lines outside Task headings

| ID | Original requirement | Source line | Disposition |
|---|---|---|---|
| P01-A001 | - [ ] Every Master Plan 01 deliverable has a task. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2620 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A002 | - [ ] No business feature from Plans 02–10 was pulled into Foundation. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2621 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A003 | - [ ] Flyway and PostgreSQL/PostGIS are exercised by real integration tests. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2622 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A004 | - [ ] No H2 fallback exists. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2623 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A005 | - [ ] Standard API error shape and request ID are established before domain controllers. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2624 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A006 | - [ ] Shared `Money` has no floating-point source field. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2625 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A007 | - [ ] `CurrentActor` contains only global roles; merchant roles are deferred. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2626 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A008 | - [ ] `OutboxEventWriter` is a port only, not a fake no-op bean. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2627 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A009 | - [ ] OpenAPI generation has a deterministic diff gate. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2628 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A010 | - [ ] Frontend uses generated types rather than handwritten API DTO duplication. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2629 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A011 | - [ ] Next.js dev proxy does not turn Next.js into the production BFF. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2630 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A012 | - [ ] Nginx blocks public `/actuator/*` in the routing skeleton. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2631 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A013 | - [ ] `.env.example` contains examples only; real secret files remain ignored. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2632 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A014 | - [ ] CI and local verification run the same core commands. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2633 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P01-A015 | - [ ] Final acceptance uses fresh verification evidence. | docs/CoVie-technical-plan-01-foundation-repository-v1.0.md:2634 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |

Inventory: 12Tasks / 89Step headings / 15explicit checklist lines selected by stated heading rule. Normative details and in-code acceptance examples remain at the linked original locations; this index is not a claim that prose/code blocks were exhaustively audited.

## Plan02: identity/auth

Original: `docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md`.

Source areas: `apps/api/src/main/java/vn/covie/auth/; apps/api/src/main/java/vn/covie/users/; apps/web/app/(auth)/`.

Evidence to retain: `docs/backend-evidence-matrix-2026-10-11.md; docs/operations/release-evidence-gaps-2026-10-13.md`. Read the individual method/recorded result before changing any row into a new PASS claim.

### Task identities

| ID | Exact original Task | Source line | Individual assertion status |
|---|---|---|---|
| P02-T01 | Add Security/Spatial Dependencies and Create Identity/Auth Migrations | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:237 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T02 | Implement User, Identity, Role, Audit and Auth Persistence Models | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:557 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T03 | Implement Email Normalization, Argon2id Password Policy and Opaque Token Utilities | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:871 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T04 | Implement RSA JWT Issuance, Validation and Security Cookies | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1023 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T05 | Configure Spring Security, Cookie/Bearer Authentication, Active Session Checks and CSRF | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1234 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T06 | Implement Google OIDC Start/Callback, Existing-User Login and Auth Action Tickets | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1499 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T07 | Complete First Registration Through Password Onboarding | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1772 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T08 | Implement Email/Password Login Without Account Enumeration | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1950 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T09 | Implement Refresh Rotation, Strict Reuse Detection, Logout and Multi-Device Session Management | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2069 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T10 | Implement Google-Verified Password Recovery and Global Session Revocation | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2264 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T11 | Implement `/me` Profile and User Contact Methods | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2379 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T12 | Implement Saved Addresses on PostGIS Geography | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2530 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T13 | Implement Minimal Next.js Auth UX and CSRF-Aware Browser Client | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2677 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T14 | Generate Auth/User OpenAPI Client and Add `/me`, Sessions and Address Web Reads | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2933 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T15 | Add Full Authentication Security, Transaction and IDOR Test Matrix | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3082 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P02-T16 | CI/Documentation/Acceptance Gate and Plan 03 Handoff | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3225 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |

### Child Step identities — historical directions, not new bugs

| ID | Exact original Step | Source line |
|---|---|---|
| P02-T01-S01 | Write the migration integration test first | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:249 |
| P02-T01-S02 | Run the test and verify RED | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:295 |
| P02-T01-S03 | Add Maven dependencies | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:304 |
| P02-T01-S04 | Create `V002__identity_users_and_audit.sql` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:339 |
| P02-T01-S05 | Create `V003__auth_sessions_and_tickets.sql` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:464 |
| P02-T01-S06 | Run migration tests and inspect schema | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:529 |
| P02-T01-S07 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:548 |
| P02-T02-S01 | Write identity uniqueness repository tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:579 |
| P02-T02-S02 | Run RED | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:614 |
| P02-T02-S03 | Implement enums exactly | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:623 |
| P02-T02-S04 | Implement `UserEntity` with optimistic versioning | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:641 |
| P02-T02-S05 | Implement `UserIdentityEntity` factories | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:695 |
| P02-T02-S06 | Implement `UserAddressEntity` using Hibernate Spatial geography | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:712 |
| P02-T02-S07 | Implement repositories with explicit auth lookup methods | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:730 |
| P02-T02-S08 | Implement `AuditLogWriter` as the single write service | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:815 |
| P02-T02-S09 | Run persistence tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:853 |
| P02-T02-S10 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:862 |
| P02-T03-S01 | Write normalization tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:884 |
| P02-T03-S02 | Implement `EmailNormalizer` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:900 |
| P02-T03-S03 | Write password tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:916 |
| P02-T03-S04 | Configure versioned Argon2id | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:928 |
| P02-T03-S05 | Implement opaque token generation | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:961 |
| P02-T03-S06 | Implement token fingerprinting | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:976 |
| P02-T03-S07 | Verify token tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:994 |
| P02-T03-S08 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1014 |
| P02-T04-S01 | Add auth configuration properties | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1040 |
| P02-T04-S02 | Write JWT round-trip test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1099 |
| P02-T04-S03 | Implement `JwtConfiguration` with Spring Security `JwtEncoder`/`JwtDecoder` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1113 |
| P02-T04-S04 | Implement `JwtTokenService` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1132 |
| P02-T04-S05 | Implement cookies | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1167 |
| P02-T04-S06 | Add local key generation script | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1191 |
| P02-T04-S07 | Run tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1216 |
| P02-T04-S08 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1225 |
| P02-T05-S01 | Write security behavior tests before configuration | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1254 |
| P02-T05-S02 | Extend shared error codes and add request-id accessor | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1273 |
| P02-T05-S03 | Implement cookie-or-header bearer resolution | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1288 |
| P02-T05-S04 | Implement CSRF matcher | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1327 |
| P02-T05-S05 | Configure Spring Security SPA CSRF | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1333 |
| P02-T05-S06 | Implement `/api/v1/auth/csrf` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1409 |
| P02-T05-S07 | Implement active-session validation after JWT authentication | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1428 |
| P02-T05-S08 | Implement `CurrentActorProvider` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1457 |
| P02-T05-S09 | Verify security tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1482 |
| P02-T05-S10 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1491 |
| P02-T06-S01 | Add Google client configuration | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1518 |
| P02-T06-S02 | Define explicit intent | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1547 |
| P02-T06-S03 | Implement the public start controller | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1558 |
| P02-T06-S04 | Configure Google redirection endpoint and success/failure handlers | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1580 |
| P02-T06-S05 | Write `GoogleAuthServiceTest` for canonical identity behavior | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1596 |
| P02-T06-S06 | Implement OIDC claim extraction | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1613 |
| P02-T06-S07 | Implement action ticket creation | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1633 |
| P02-T06-S08 | Implement existing-Google-user login branch | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1659 |
| P02-T06-S09 | Implement `InitialSessionIssuer` and device metadata | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1680 |
| P02-T06-S10 | Implement new-user onboarding branch | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1709 |
| P02-T06-S11 | Implement password-reset intent branch | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1722 |
| P02-T06-S12 | Invalidate temporary HTTP session after OAuth completes | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1736 |
| P02-T06-S13 | Implement generic OAuth failure behavior | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1740 |
| P02-T06-S14 | Verify handlers | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1754 |
| P02-T06-S15 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1763 |
| P02-T07-S01 | Define onboarding request | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1784 |
| P02-T07-S02 | Write the full onboarding integration test first | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1795 |
| P02-T07-S03 | Run RED | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1825 |
| P02-T07-S04 | Implement action ticket lookup/consume with row lock | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1834 |
| P02-T07-S05 | Implement one transaction | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1858 |
| P02-T07-S06 | Reuse the shared initial-session output | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1879 |
| P02-T07-S07 | Add duplicate-submit protection through ticket single-use | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1883 |
| P02-T07-S08 | Implement controller and cookie response | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1899 |
| P02-T07-S09 | Verify controller/security cases | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1919 |
| P02-T07-S10 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1941 |
| P02-T08-S01 | Define request | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1961 |
| P02-T08-S02 | Write enumeration-safe tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1969 |
| P02-T08-S03 | Implement authentication algorithm | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:1996 |
| P02-T08-S04 | Keep multi-device semantics | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2016 |
| P02-T08-S05 | Implement controller | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2020 |
| P02-T08-S06 | Audit failure without leaking secrets and without rollback loss | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2039 |
| P02-T08-S07 | Verify | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2051 |
| P02-T08-S08 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2060 |
| P02-T09-S01 | Write rotation test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2085 |
| P02-T09-S02 | Write strict reuse test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2101 |
| P02-T09-S03 | Implement refresh transaction with row locking and committed reuse revocation | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2116 |
| P02-T09-S04 | Define strict concurrency behavior | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2164 |
| P02-T09-S05 | Implement `/api/v1/auth/refresh` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2177 |
| P02-T09-S06 | Implement logout | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2183 |
| P02-T09-S07 | Implement session list | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2197 |
| P02-T09-S08 | Implement revoke-one and revoke-others | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2219 |
| P02-T09-S09 | Verify concurrency and API | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2246 |
| P02-T09-S10 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2255 |
| P02-T10-S01 | Write full recovery integration test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2275 |
| P02-T10-S02 | Define request | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2303 |
| P02-T10-S03 | Implement one transaction | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2312 |
| P02-T10-S04 | Enforce fresh Google action-ticket TTL | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2332 |
| P02-T10-S05 | Implement controller | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2336 |
| P02-T10-S06 | Verify negative cases | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2352 |
| P02-T10-S07 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2371 |
| P02-T11-S01 | Define `MeResponse` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2392 |
| P02-T11-S02 | Write authorization test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2406 |
| P02-T11-S03 | Implement profile read/update | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2414 |
| P02-T11-S04 | Define contact API | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2444 |
| P02-T11-S05 | Implement primary-contact transaction | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2475 |
| P02-T11-S06 | Enforce ownership | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2491 |
| P02-T11-S07 | Soft-deactivate on DELETE | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2501 |
| P02-T11-S08 | Run tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2512 |
| P02-T11-S09 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2521 |
| P02-T12-S01 | Define address create request without premature LocationArea coupling | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2544 |
| P02-T12-S02 | Write PostGIS persistence test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2561 |
| P02-T12-S03 | Implement address CRUD | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2588 |
| P02-T12-S04 | Implement default-address invariant | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2600 |
| P02-T12-S05 | Implement soft delete | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2613 |
| P02-T12-S06 | Define response | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2625 |
| P02-T12-S07 | Verify ownership/validation | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2646 |
| P02-T12-S08 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2668 |
| P02-T13-S01 | Implement CSRF token loader | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2696 |
| P02-T13-S02 | Add CSRF middleware to browser API client | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2759 |
| P02-T13-S03 | Implement Google login button | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2781 |
| P02-T13-S04 | Implement Email/Password login form | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2792 |
| P02-T13-S05 | Implement onboarding password page | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2807 |
| P02-T13-S06 | Implement reset-password page | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2821 |
| P02-T13-S07 | Implement single-flight refresh coordination in `auth-session.ts` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2834 |
| P02-T13-S08 | Write frontend tests | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2898 |
| P02-T13-S09 | Verify | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2914 |
| P02-T13-S10 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2924 |
| P02-T14-S01 | Add OpenAPI contract test | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2952 |
| P02-T14-S02 | Document auth headers/cookies without exposing secret values | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2971 |
| P02-T14-S03 | Regenerate typed client | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2984 |
| P02-T14-S04 | Implement an authenticated server-read client and `require-user.ts` | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:2994 |
| P02-T14-S05 | Implement minimal `/me` page | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3037 |
| P02-T14-S06 | Implement session list | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3051 |
| P02-T14-S07 | Implement address list/create baseline | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3057 |
| P02-T14-S08 | Verify contract drift | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3061 |
| P02-T14-S09 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3073 |
| P02-T15-S01 | Test complete first-registration path | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3093 |
| P02-T15-S02 | Test returning login paths resolve same user | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3110 |
| P02-T15-S03 | Test refresh/session contract | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3118 |
| P02-T15-S04 | Test password recovery contract | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3133 |
| P02-T15-S05 | Test CSRF matrix | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3144 |
| P02-T15-S06 | Test private-resource IDOR | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3156 |
| P02-T15-S07 | Test audit/log safety | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3169 |
| P02-T15-S08 | Test account-status and onboarding-race enforcement | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3184 |
| P02-T15-S09 | Test database invariants | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3193 |
| P02-T15-S10 | Run acceptance suite | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3207 |
| P02-T15-S11 | Commit | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3216 |
| P02-T16-S01 | Extend CI without real Google credentials | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3239 |
| P02-T16-S02 | Document local Google OAuth setup | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3263 |
| P02-T16-S03 | Document JWT key handling | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3283 |
| P02-T16-S04 | Document auth architecture decisions | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3296 |
| P02-T16-S05 | Fresh backend verification | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3311 |
| P02-T16-S06 | Fresh frontend/contract verification | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3328 |
| P02-T16-S07 | Verify migration ordering | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3344 |
| P02-T16-S08 | Verify secrets are not tracked | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3364 |
| P02-T16-S09 | Commit docs/CI/contract | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3373 |

Child status: NOT_VERIFIED_INDIVIDUALLY; existing module/test evidence is retained without implying each procedural step independently passed. No test rerun or source change is authorized solely by these index entries.

### Explicit checklist/gate lines outside Task headings

| ID | Original requirement | Source line | Disposition |
|---|---|---|---|
| P02-A001 | - [ ] Plan 01 interfaces are reused rather than duplicated. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3599 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A002 | - [ ] Migration numbering no longer jumps to V014 before later domain migrations. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3600 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A003 | - [ ] `users` contains no email/password fields. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3601 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A004 | - [ ] Google `sub` is the identity key. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3602 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A005 | - [ ] New Google callback alone cannot create a fully authenticated user. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3603 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A006 | - [ ] Onboarding/recovery raw ticket is never placed in URL or DB. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3604 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A007 | - [ ] Local EMAIL uniqueness does not incorrectly block a GOOGLE row carrying the same email metadata. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3605 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A008 | - [ ] Password hash has algorithm/version prefix. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3606 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A009 | - [ ] No plaintext password/token is logged/audited. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3607 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A010 | - [ ] JWT global roles contain only USER/ADMIN. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3608 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A011 | - [ ] Merchant roles remain deferred to Plan 03 DB membership checks. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3609 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A012 | - [ ] Refresh/revoke/password-reset mutations use the same session-first, token-second lock order. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3610 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A013 | - [ ] Strict reuse detection has a real concurrent PostgreSQL test. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3611 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A014 | - [ ] Logout and session revoke invalidate future requests through active-session validation. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3612 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A015 | - [ ] CSRF is enabled for browser unsafe requests. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3613 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A016 | - [ ] Bearer-header clients do not need browser CSRF. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3614 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A017 | - [ ] `/me` and child resources derive user ID from `CurrentActor`. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3615 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A018 | - [ ] IDOR returns 404 for another user's private IDs. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3616 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A019 | - [ ] PostGIS address test catches lat/lng reversal. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3617 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A020 | - [ ] `location_area_id`/`avatar_media_id` FKs are intentionally deferred until Plan 03 tables exist. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3618 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A021 | - [ ] OpenAPI/client generation remains deterministic. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3619 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |
| P02-A022 | - [ ] Plan 02 contains no merchant/catalog/order business implementation. | docs/CoVie-technical-plan-02-identity-auth-user-v1.0.md:3620 | NOT_VERIFIED_INDIVIDUALLY; reconcile with bounded ledger |

Inventory: 16Tasks / 149Step headings / 22explicit checklist lines selected by stated heading rule. Normative details and in-code acceptance examples remain at the linked original locations; this index is not a claim that prose/code blocks were exhaustively audited.

## Plan03: merchant/catalog/media

Original: `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md`.

Source areas: `apps/api/src/main/java/vn/covie/merchants/; apps/api/src/main/java/vn/covie/catalog/; apps/api/src/main/java/vn/covie/media/; apps/web/features/merchant/`.

Evidence to retain: `docs/backend-evidence-matrix-2026-10-11.md; docs/plans/social-product-card/v2-final-local-acceptance-2026-10-17.md`. Read the individual method/recorded result before changing any row into a new PASS claim.

### Task identities

| ID | Exact original Task | Source line | Individual assertion status |
|---|---|---|---|
| P03-T01 | Add Plan 03 Dependencies and Create V004–V006 Migrations | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:391 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T02 | Implement Shared ETag/If-Match, Idempotency and Outbox Persistence | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:925 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T03 | Implement Location Areas and Explicit LocationContext Resolution | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1189 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T04 | Implement Merchant Persistence and Database-Resolved Access Policy | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1370 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T05 | Implement Merchant Profile and Membership APIs | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1550 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T06 | Implement Merchant Locations, STAFF Assignments, Hours and Contacts | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1732 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T07 | Implement S3-Compatible Object Storage and Presigned Upload Infrastructure | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1910 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T08 | Implement Media Upload, Verification and Attachment Policy | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2069 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T09 | Implement Category and Catalog Persistence Models | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2272 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T10 | Implement Aggregate Catalog Creation with Variants, Attributes, Media and Location Offers | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2442 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T11 | Implement Granular Catalog Editing and Offer-Level STAFF Operations | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2660 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T12 | Implement Catalog Publish/Pause/Activate/Archive State Machine | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2792 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T13 | Implement Public Catalog Detail with Concrete Location Resolution | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2909 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T14 | Implement PostgreSQL/PostGIS Public Catalog Discovery and Opaque Cursor Pagination | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3022 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T15 | Add Seller Management Read Models and Basic Seller Web Slice | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3294 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T16 | Add Direct Media Upload and Public Discovery Web Slice | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3395 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P03-T17 | Verify OpenAPI, Security Matrix, Search Invariants and Plan 03 Acceptance Gate | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3504 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |

### Child Step identities — historical directions, not new bugs

| ID | Exact original Step | Source line |
|---|---|---|
| P03-T01-S01 | Write the migration integration test before creating the migrations | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:404 |
| P03-T01-S02 | Add AWS SDK S3 dependency through one pinned BOM property | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:461 |
| P03-T01-S03 | Create `V004__location_merchant_media.sql` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:494 |
| P03-T01-S04 | Create `V005__catalog_and_search.sql` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:688 |
| P03-T01-S05 | Create `V006__idempotency_and_outbox.sql` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:868 |
| P03-T01-S06 | Run the migration test and validate Flyway order | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:907 |
| P03-T01-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:916 |
| P03-T02-S01 | Write ETag parsing tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:944 |
| P03-T02-S02 | Implement `VersionPrecondition` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:966 |
| P03-T02-S03 | Write idempotency behavior tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1019 |
| P03-T02-S04 | Implement canonical request hashing | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1039 |
| P03-T02-S05 | Implement `IdempotencyExecutor` using a PostgreSQL transaction advisory lock | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1074 |
| P03-T02-S06 | Implement outbox persistence against the Plan 01 port | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1143 |
| P03-T02-S07 | Run shared infrastructure tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1171 |
| P03-T02-S08 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1180 |
| P03-T03-S01 | Define the canonical `LocationContext` value | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1212 |
| P03-T03-S02 | Write resolver tests first | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1241 |
| P03-T03-S03 | Implement geometry creation once | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1256 |
| P03-T03-S04 | Implement `LocationContextResolver` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1280 |
| P03-T03-S05 | Implement public location-area tree read | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1330 |
| P03-T03-S06 | Extend Plan 02 security rules for public marketplace GET routes | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1338 |
| P03-T03-S07 | Run focused tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1352 |
| P03-T03-S08 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1361 |
| P03-T04-S01 | Model merchant enums exactly | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1405 |
| P03-T04-S02 | Implement `@Version` on mutable resources | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1416 |
| P03-T04-S03 | Add access-oriented repository queries | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1428 |
| P03-T04-S04 | Define capabilities instead of scattering role comparisons | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1453 |
| P03-T04-S05 | Implement `MerchantAccessPolicy.require(...)` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1470 |
| P03-T04-S06 | Write authorization tests that prove DB revocation takes effect next request | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1518 |
| P03-T04-S07 | Run tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1532 |
| P03-T04-S08 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1541 |
| P03-T05-S01 | Define merchant create request/response | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1571 |
| P03-T05-S02 | Implement slug normalization and collision handling | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1589 |
| P03-T05-S03 | Implement merchant creation atomically | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1607 |
| P03-T05-S04 | Implement list/current-role endpoint | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1634 |
| P03-T05-S05 | Implement `GET/PATCH /api/v1/merchants/{merchantId}` with ETag | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1642 |
| P03-T05-S06 | Define member-add request using existing local email identity | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1668 |
| P03-T05-S07 | Implement last-owner protection | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1687 |
| P03-T05-S08 | Implement role update/revoke semantics | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1704 |
| P03-T05-S09 | Run controller tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1714 |
| P03-T05-S10 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1723 |
| P03-T06-S01 | Define location create/update contract | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1757 |
| P03-T06-S02 | Validate timezone and area | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1777 |
| P03-T06-S03 | Implement create/update/delete with ETag | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1793 |
| P03-T06-S04 | Implement STAFF location assignment endpoint | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1804 |
| P03-T06-S05 | Define weekly hours replace contract | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1833 |
| P03-T06-S06 | Implement exception precedence and `isOpen(...)` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1857 |
| P03-T06-S07 | Implement contacts | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1877 |
| P03-T06-S08 | Run location tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1892 |
| P03-T06-S09 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1901 |
| P03-T07-S01 | Add storage configuration properties | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1926 |
| P03-T07-S02 | Define the storage port | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1958 |
| P03-T07-S03 | Configure `S3Client` and `S3Presigner` with the same endpoint/style | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:1970 |
| P03-T07-S04 | Implement presigned PUT with signed content type | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2001 |
| P03-T07-S05 | Implement metadata/head and prefix read | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2021 |
| P03-T07-S06 | Write a MinIO integration test | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2036 |
| P03-T07-S07 | Run storage test | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2051 |
| P03-T07-S08 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2060 |
| P03-T08-S01 | Define V1 media purposes and statuses | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2091 |
| P03-T08-S02 | Define create upload contract | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2110 |
| P03-T08-S03 | Create deterministic storage keys without user filenames | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2136 |
| P03-T08-S04 | Enforce upload quota before presigning | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2153 |
| P03-T08-S05 | Implement image magic sniffing | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2165 |
| P03-T08-S06 | Implement upload completion | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2175 |
| P03-T08-S07 | Implement `MediaAttachmentPolicy` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2212 |
| P03-T08-S08 | Implement public URL resolver | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2232 |
| P03-T08-S09 | Implement orphan cleanup use case for the later scheduler | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2246 |
| P03-T08-S10 | Run media tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2254 |
| P03-T08-S11 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2263 |
| P03-T09-S01 | Model catalog enums | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2320 |
| P03-T09-S02 | Map join tables with explicit composite IDs and mutable resources with `@Version` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2334 |
| P03-T09-S03 | Implement repository mappings for composite joins without cascade surprises | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2363 |
| P03-T09-S04 | Keep search vector outside JPA entity mutation | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2374 |
| P03-T09-S05 | Add repository methods later plans need | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2378 |
| P03-T09-S06 | Implement category public read | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2399 |
| P03-T09-S07 | Run persistence/category tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2424 |
| P03-T09-S08 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2433 |
| P03-T10-S01 | Define explicit aggregate request DTOs | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2461 |
| P03-T10-S02 | Write validation tests before implementation | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2504 |
| P03-T10-S03 | Implement category/attribute validation | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2529 |
| P03-T10-S04 | Implement variant-combination normalization | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2535 |
| P03-T10-S05 | Implement DRAFT aggregate transaction through `IdempotencyExecutor` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2549 |
| P03-T10-S06 | Implement default variant exactly once | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2576 |
| P03-T10-S07 | Implement offer invariants | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2585 |
| P03-T10-S08 | Implement search-document rebuild | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2616 |
| P03-T10-S09 | Run create tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2642 |
| P03-T10-S10 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2651 |
| P03-T11-S01 | Implement item PATCH | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2674 |
| P03-T11-S02 | Implement variant PATCH | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2703 |
| P03-T11-S03 | Implement location-offer PATCH | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2727 |
| P03-T11-S04 | Do not use item ETag to edit a child offer | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2752 |
| P03-T11-S05 | Map concurrent JPA conflict to the same 409 contract | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2764 |
| P03-T11-S06 | Run edit tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2774 |
| P03-T11-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2783 |
| P03-T12-S01 | Define publish policy port | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2806 |
| P03-T12-S02 | Define lifecycle transitions | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2830 |
| P03-T12-S03 | Implement `CatalogPublishValidator` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2843 |
| P03-T12-S04 | Implement command endpoints | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2861 |
| P03-T12-S05 | Append outbox intents in the same transaction | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2872 |
| P03-T12-S06 | Run lifecycle tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2891 |
| P03-T12-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2900 |
| P03-T13-S01 | Define detail location precedence | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2928 |
| P03-T13-S02 | Define a public detail response without JPA leakage | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2947 |
| P03-T13-S03 | Implement visibility | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2976 |
| P03-T13-S04 | Expose public merchant detail | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:2992 |
| P03-T13-S05 | Ensure contact privacy | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3000 |
| P03-T13-S06 | Run detail tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3004 |
| P03-T13-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3013 |
| P03-T14-S01 | Define typed query object and whitelist | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3042 |
| P03-T14-S02 | Normalize bounds | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3075 |
| P03-T14-S03 | Define cursor payload per sort and sign its shape, not its content | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3089 |
| P03-T14-S04 | Implement nearest-location candidate CTE | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3105 |
| P03-T14-S05 | Add FTS relevance predicate/ranking | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3186 |
| P03-T14-S06 | Add remaining filters as bind-parameter predicates | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3214 |
| P03-T14-S07 | Implement keyset predicates per sort | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3227 |
| P03-T14-S08 | Define summary response | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3241 |
| P03-T14-S09 | Run search/cursor tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3258 |
| P03-T14-S10 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3285 |
| P03-T15-S01 | Regenerate API client after backend endpoints compile | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3316 |
| P03-T15-S02 | Implement merchant API wrapper using generated client only | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3328 |
| P03-T15-S03 | Implement minimal seller pages | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3345 |
| P03-T15-S04 | Carry ETag in edit forms | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3359 |
| P03-T15-S05 | Write frontend tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3365 |
| P03-T15-S06 | Run frontend checks | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3376 |
| P03-T15-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3386 |
| P03-T16-S01 | Implement direct upload orchestration | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3412 |
| P03-T16-S02 | Validate client file before requesting presign | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3435 |
| P03-T16-S03 | Implement explicit location state for discovery | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3446 |
| P03-T16-S04 | Implement minimal `/explore` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3458 |
| P03-T16-S05 | Implement `/items/[itemId]` | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3470 |
| P03-T16-S06 | Run web tests and build | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3484 |
| P03-T16-S07 | Commit | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3495 |
| P03-T17-S01 | Extend OpenAPI assertions | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3516 |
| P03-T17-S02 | Write the end-to-end backend marketplace acceptance test | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3538 |
| P03-T17-S03 | Write authorization matrix test | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3558 |
| P03-T17-S04 | Write cross-table invariant tests | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3569 |
| P03-T17-S05 | Run the complete backend suite | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3584 |
| P03-T17-S06 | Re-export and verify generated API client | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3593 |
| P03-T17-S07 | Run full frontend quality gate | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3606 |
| P03-T17-S08 | Validate migrations from an empty database | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3617 |
| P03-T17-S09 | Run static secret scan on changed files | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3631 |
| P03-T17-S10 | Commit acceptance artifacts/docs | docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md:3639 |

Child status: NOT_VERIFIED_INDIVIDUALLY; existing module/test evidence is retained without implying each procedural step independently passed. No test rerun or source change is authorized solely by these index entries.

Inventory: 17Tasks / 142Step headings / 0explicit checklist lines selected by stated heading rule. Normative details and in-code acceptance examples remain at the linked original locations; this index is not a claim that prose/code blocks were exhaustively audited.

## Plan04: shipping/cart/inventory/checkout

Original: `docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md`.

Source areas: `apps/api/src/main/java/vn/covie/shipping/; apps/api/src/main/java/vn/covie/cart/; apps/api/src/main/java/vn/covie/inventory/; apps/api/src/main/java/vn/covie/checkout/`.

Evidence to retain: `docs/backend-evidence-matrix-2026-10-11.md; docs/backend-progress-review-2026-10-12.md`. Read the individual method/recorded result before changing any row into a new PASS claim.

### Task identities

| ID | Exact original Task | Source line | Individual assertion status |
|---|---|---|---|
| P04-T01 | Add Delivery, Shipping, Inventory, Cart, Checkout and Order-Creation Migrations | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:404 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T02 | Implement Seller Delivery Coverage Management | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:855 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T03 | Implement Radius + Area Delivery Coverage Evaluation | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:970 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T04 | Implement Seller Shipping Policy Management | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1056 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T05 | Implement Shipping Evaluation and Immutable Snapshot | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1154 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T06 | Implement Inventory Persistence, Locking and TRACKED Balance Coordination | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1232 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T07 | Implement Inventory Read and Adjustment Commands | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1342 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T08 | Implement Per-Item Bulk Stock Set | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1463 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T09 | Implement Multi-Cart Add-to-Cart Semantics | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1631 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T10 | Implement Cart Read, Quantity Update and Remove with ETag | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1727 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T11 | Define Unified Checkout Candidate Contracts and CART/DIRECT Resolvers | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1797 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T12 | Implement Checkout Validation, Pricing and Preview | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1891 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T13 | Implement Opaque Single-Use Checkout Sessions | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1989 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T14 | Wire `POST /checkouts/preview` and Preview API Contract | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2082 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T15 | Implement Order Creation Foundation and Immutable Snapshot Factory | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2135 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T16 | Implement Reservation/Movement Write Path for Checkout | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2279 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T17 | Implement Atomic Checkout Commit + Change Detection | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2342 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T18 | Prove Last-Stock Concurrency and Rollback Invariants on Real PostgreSQL | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2518 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T19 | Extend Public Catalog Discovery with Fulfillment Filters | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2603 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T20 | Add Minimal Buyer/Seller Web Slices for Cart, Checkout, Inventory and Shipping | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2661 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P04-T21 | Refresh OpenAPI Client and Run Full Plan 04 Acceptance Gate | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2767 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |

### Child Step identities — historical directions, not new bugs

| ID | Exact original Step | Source line |
|---|---|---|
| P04-T01-S01 | Write migration integration test before migrations exist | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:416 |
| P04-T01-S02 | Run the migration test and verify red | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:441 |
| P04-T01-S03 | Create `V007__delivery_shipping_inventory.sql` | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:450 |
| P04-T01-S04 | Create `V008__carts_and_checkout_sessions.sql` | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:622 |
| P04-T01-S05 | Create `V009__order_creation_foundation.sql` | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:687 |
| P04-T01-S06 | Add indexes required by Plan 04 queries | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:821 |
| P04-T01-S07 | Run migration tests and verify green | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:837 |
| P04-T01-S08 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:846 |
| P04-T02-S01 | Write service tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:877 |
| P04-T02-S02 | Implement entities and repository | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:890 |
| P04-T02-S03 | Implement create/update/delete transaction | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:905 |
| P04-T02-S04 | Implement controller with ETag | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:933 |
| P04-T02-S05 | Add stable errors | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:944 |
| P04-T02-S06 | Run focused tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:952 |
| P04-T02-S07 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:961 |
| P04-T03-S01 | Write geographic behavior tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:983 |
| P04-T03-S02 | Define immutable result | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:996 |
| P04-T03-S03 | Implement SQL for distance and descendant area | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1012 |
| P04-T03-S04 | Implement evaluator | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1024 |
| P04-T03-S05 | Run tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1038 |
| P04-T03-S06 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1047 |
| P04-T04-S01 | Write validation tests for all four modes | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1081 |
| P04-T04-S02 | Implement enums and JPA mappings | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1099 |
| P04-T04-S03 | Implement replace-rules transaction | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1110 |
| P04-T04-S04 | Implement controller and errors | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1126 |
| P04-T04-S05 | Run tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1138 |
| P04-T04-S06 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1145 |
| P04-T05-S01 | Write pure evaluation tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1167 |
| P04-T05-S02 | Define result type | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1183 |
| P04-T05-S03 | Implement evaluator as deterministic pure logic after policy load | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1197 |
| P04-T05-S04 | Build snapshot with exact evaluation inputs | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1201 |
| P04-T05-S05 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1221 |
| P04-T06-S01 | Map inventory entities without entity cascades | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1255 |
| P04-T06-S02 | Add lock query | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1281 |
| P04-T06-S03 | Write TRACKED transition tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1297 |
| P04-T06-S04 | Implement `InventoryModeCoordinator` | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1309 |
| P04-T06-S05 | Run tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1324 |
| P04-T06-S06 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1333 |
| P04-T07-S01 | Write set-stock/stock-in/stock-out tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1360 |
| P04-T07-S02 | Implement single-row transaction | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1372 |
| P04-T07-S03 | Define request DTOs | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1404 |
| P04-T07-S04 | Implement query DTO | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1420 |
| P04-T07-S05 | Implement endpoints and errors | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1440 |
| P04-T07-S06 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1452 |
| P04-T08-S01 | Write partial-success test | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1480 |
| P04-T08-S02 | Implement independent per-item transactions with derived idempotency | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1502 |
| P04-T08-S03 | Enforce V1 batch bound | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1609 |
| P04-T08-S04 | Run test and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1620 |
| P04-T09-S01 | Write cart-routing tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1651 |
| P04-T09-S02 | Map cart entities | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1663 |
| P04-T09-S03 | Implement race-safe find-or-create active Cart | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1669 |
| P04-T09-S04 | Validate offer before adding | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1693 |
| P04-T09-S05 | Implement endpoint | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1708 |
| P04-T09-S06 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1716 |
| P04-T10-S01 | Write read/current-price tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1744 |
| P04-T10-S02 | Implement read DTO | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1756 |
| P04-T10-S03 | Implement PATCH/DELETE aggregate precondition | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1772 |
| P04-T10-S04 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1786 |
| P04-T11-S01 | Define candidate contract | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1816 |
| P04-T11-S02 | Define resolver SPI for Plan 06 extension | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1840 |
| P04-T11-S03 | Implement CART resolver | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1851 |
| P04-T11-S04 | Implement DIRECT resolver | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1863 |
| P04-T11-S05 | Keep NEED_REQUEST additive | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1876 |
| P04-T11-S06 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1880 |
| P04-T12-S01 | Write preview rule matrix tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1908 |
| P04-T12-S02 | Define normalized preview result | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1925 |
| P04-T12-S03 | Implement current-state validation in bulk | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1929 |
| P04-T12-S04 | Validate minimum order after current subtotal | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1943 |
| P04-T12-S05 | Add errors | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1957 |
| P04-T12-S06 | Run preview tests and verify no reservation | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1971 |
| P04-T12-S07 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:1980 |
| P04-T13-S01 | Write token/security tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2006 |
| P04-T13-S02 | Implement 256-bit opaque token | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2016 |
| P04-T13-S03 | Configure TTL | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2033 |
| P04-T13-S04 | Add locked lookup | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2043 |
| P04-T13-S05 | Persist canonical request/preview snapshots | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2053 |
| P04-T13-S06 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2071 |
| P04-T14-S01 | Write controller tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2094 |
| P04-T14-S02 | Implement orchestration | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2108 |
| P04-T14-S03 | Return resolved LocationContext/fulfillment details | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2120 |
| P04-T14-S04 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2124 |
| P04-T15-S01 | Map creation-only Order enum set | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2164 |
| P04-T15-S02 | Implement collision-safe display Order code | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2177 |
| P04-T15-S03 | Implement immutable snapshot records | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2187 |
| P04-T15-S04 | Implement creation transaction helper with mandatory propagation | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2240 |
| P04-T15-S05 | Write event assertion | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2264 |
| P04-T15-S06 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2268 |
| P04-T16-S01 | Write reservation tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2292 |
| P04-T16-S02 | Implement balance mutation methods | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2302 |
| P04-T16-S03 | Implement reservation service with MANDATORY transaction | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2319 |
| P04-T16-S04 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2331 |
| P04-T17-S01 | Write commit happy-path test | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2362 |
| P04-T17-S02 | Write change-detection tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2389 |
| P04-T17-S03 | Implement comparison as canonical semantic comparison | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2426 |
| P04-T17-S04 | Implement outer IdempotencyExecutor call | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2430 |
| P04-T17-S05 | Implement exact Commit lock/order sequence | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2454 |
| P04-T17-S06 | Handle expired/consumed token | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2480 |
| P04-T17-S07 | Implement response | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2495 |
| P04-T17-S08 | Run tests and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2507 |
| P04-T18-S01 | Write two-buyer last-stock test | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2528 |
| P04-T18-S02 | Write multi-item deadlock-order test | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2557 |
| P04-T18-S03 | Write rollback injection test | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2563 |
| P04-T18-S04 | Run concurrency suite repeatedly | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2583 |
| P04-T18-S05 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2594 |
| P04-T19-S01 | Add typed filters | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2616 |
| P04-T19-S02 | Preserve one resolved MerchantLocation rule | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2628 |
| P04-T19-S03 | Avoid shipping evaluator per result N+1 | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2639 |
| P04-T19-S04 | Run search tests + explain plan | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2643 |
| P04-T19-S05 | Commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2652 |
| P04-T20-S01 | Implement cart API wrapper without duplicating DTOs | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2691 |
| P04-T20-S02 | Implement cart list grouped by MerchantLocation/type | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2703 |
| P04-T20-S03 | Implement Checkout Preview state | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2707 |
| P04-T20-S04 | Implement seller inventory table | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2722 |
| P04-T20-S05 | Implement coverage and shipping forms | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2735 |
| P04-T20-S06 | Add frontend tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2741 |
| P04-T20-S07 | Run frontend quality gate and commit | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2753 |
| P04-T21-S01 | Generate OpenAPI and TypeScript client | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2778 |
| P04-T21-S02 | Run all backend tests | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2791 |
| P04-T21-S03 | Run concurrency suite again after full integration | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2800 |
| P04-T21-S04 | Run frontend gates | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2811 |
| P04-T21-S05 | Validate clean database migration V001 -> V009 | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2821 |
| P04-T21-S06 | Manual smoke flow | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2833 |
| P04-T21-S07 | Commit generated contract/acceptance updates | docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md:2852 |

Child status: NOT_VERIFIED_INDIVIDUALLY; existing module/test evidence is retained without implying each procedural step independently passed. No test rerun or source change is authorized solely by these index entries.

Inventory: 21Tasks / 123Step headings / 0explicit checklist lines selected by stated heading rule. Normative details and in-code acceptance examples remain at the linked original locations; this index is not a claim that prose/code blocks were exhaustively audited.

## Plan05: orders/fulfillment

Original: `docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md`.

Source areas: `apps/api/src/main/java/vn/covie/orders/; apps/api/src/test/java/vn/covie/orders/; apps/web/features/orders/`.

Evidence to retain: `docs/backend-evidence-matrix-2026-10-11.md; docs/backend-progress-review-2026-10-12.md`. Read the individual method/recorded result before changing any row into a new PASS claim.

### Task identities

| ID | Exact original Task | Source line | Individual assertion status |
|---|---|---|---|
| P05-T01 | Add V010 Order Lifecycle Schema Migration | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:359 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T02 | Extend Order Enums, Entities and Locking Repositories | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:629 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T03 | Add Order Policy Provider and Bootstrap Timeouts | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:828 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T04 | Extend Merchant Capabilities and Implement Order Access Policy | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:924 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T05 | Implement Order Event Append Service and Outbox Naming Contract | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1070 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T06 | Implement Pure Allowed-Action Matrix | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1138 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T07 | Implement Canonical Order Read Models and Cursor Lists | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1224 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T08 | Extend Inventory Reservation Service with Release and Commit Outcomes | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1333 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T09 | Implement Shared Order Transition and Completion Services | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1440 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T10 | Implement Confirm, Reject and Pre-confirm Buyer Cancel | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1557 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T11 | Implement Post-confirm Cancellation Request and Seller Cancellation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1669 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T12 | Implement Product Seller-delivery Commands | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1788 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T13 | Implement Product Pickup and Buyer No-show Flow | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1873 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T14 | Implement Service Fulfillment Commands | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1967 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T15 | Implement Delivery and Service Issue Reporting | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2027 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T16 | Implement Scheduled Expiry, Auto-completion and Issue Closure | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2096 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T17 | Expose Canonical Order HTTP APIs and Idempotent Commands | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2260 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T18 | Add Full State/Concurrency Contract Test Matrix | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2369 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T19 | Regenerate OpenAPI Client and Add Minimal Buyer/Seller Order UI | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2458 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |
| P05-T20 | Plan 05 Acceptance Gate and Regression Verification | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2561 | NOT_VERIFIED_INDIVIDUALLY; retain bounded ledger above |

### Child Step identities — historical directions, not new bugs

| ID | Exact original Step | Source line |
|---|---|---|
| P05-T01-S01 | Write migration test before creating V010 | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:369 |
| P05-T01-S02 | Run migration test and verify expected failure | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:424 |
| P05-T01-S03 | Expand Order status + event storage in V010 | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:433 |
| P05-T01-S04 | Create cancellation request table | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:504 |
| P05-T01-S05 | Create Order issue table | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:536 |
| P05-T01-S06 | Add lifecycle query indexes | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:576 |
| P05-T01-S07 | Run migration test from empty DB | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:612 |
| P05-T01-S08 | Commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:620 |
| P05-T02-S01 | Extend enums exactly | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:652 |
| P05-T02-S02 | Map cancellation and issue entities without cascade-delete | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:725 |
| P05-T02-S03 | Extend Order event mapping with lifecycle fields | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:764 |
| P05-T02-S04 | Add lock-by-ID query | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:779 |
| P05-T02-S05 | Add cancellation/issue lock queries | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:793 |
| P05-T02-S06 | Test pessimistic lock behavior with two transactions | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:813 |
| P05-T02-S07 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:817 |
| P05-T03-S01 | Define immutable policy snapshot | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:843 |
| P05-T03-S02 | Define provider interface | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:867 |
| P05-T03-S03 | Configure bootstrap defaults | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:875 |
| P05-T03-S04 | Activate confirmation expiry in `OrderCreationService` | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:890 |
| P05-T03-S05 | Test deadline snapshot behavior | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:902 |
| P05-T03-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:914 |
| P05-T04-S01 | Extend merchant capabilities | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:940 |
| P05-T04-S02 | Define Order access result | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:961 |
| P05-T04-S03 | Implement authorization against an already loaded/locked Order | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:979 |
| P05-T04-S04 | Write IDOR matrix tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1039 |
| P05-T04-S05 | Run Plan 03 regression + Order access tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1055 |
| P05-T04-S06 | Commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1061 |
| P05-T05-S01 | Define one append signature | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1081 |
| P05-T05-S02 | Define stable outbox event names | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1097 |
| P05-T05-S03 | Test append-only behavior | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1124 |
| P05-T05-S04 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1128 |
| P05-T06-S01 | Define action context | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1149 |
| P05-T06-S02 | Write exhaustive parameterized buyer matrix tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1162 |
| P05-T06-S03 | Write seller/provider matrix | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1193 |
| P05-T06-S04 | Run pure unit tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1209 |
| P05-T06-S05 | Commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1215 |
| P05-T07-S01 | Define detail DTO without JPA entities | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1245 |
| P05-T07-S02 | Use keyset cursor `(created_at, id)` | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1273 |
| P05-T07-S03 | Build canonical detail with actor filtering | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1294 |
| P05-T07-S04 | Test 404/field visibility and cursor determinism | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1309 |
| P05-T07-S05 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1323 |
| P05-T08-S01 | Add repository query for active reservation rows sorted by locationVariantId | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1347 |
| P05-T08-S02 | Add release signature | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1358 |
| P05-T08-S03 | Add normal commit signature | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1382 |
| P05-T08-S04 | Explicitly handle already-released no-show completion | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1403 |
| P05-T08-S05 | Test ledger invariants | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1417 |
| P05-T08-S06 | Run Plan 04 inventory regressions + commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1430 |
| P05-T09-S01 | Add Order-specific error codes | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1453 |
| P05-T09-S02 | Implement lock + state guard | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1464 |
| P05-T09-S03 | Centralize completion | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1492 |
| P05-T09-S04 | Ensure command results never expose a stale `@Version` | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1520 |
| P05-T09-S05 | Test terminal behavior | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1536 |
| P05-T09-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1547 |
| P05-T10-S01 | Define command request | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1568 |
| P05-T10-S02 | Implement seller/provider confirm | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1586 |
| P05-T10-S03 | Implement merchant reject before confirmation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1614 |
| P05-T10-S04 | Implement buyer cancel before confirmation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1630 |
| P05-T10-S05 | Test races | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1648 |
| P05-T10-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1659 |
| P05-T11-S01 | Define buyer cancellation request body | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1681 |
| P05-T11-S02 | Request cancellation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1696 |
| P05-T11-S03 | Accept cancellation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1720 |
| P05-T11-S04 | Reject cancellation | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1735 |
| P05-T11-S05 | Implement merchant cancel after confirmation through canonical `/cancel` route | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1751 |
| P05-T11-S06 | Test previous-status restoration and concurrent response | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1774 |
| P05-T11-S07 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1778 |
| P05-T12-S01 | Add entity transition methods | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1798 |
| P05-T12-S02 | Implement mark-out-for-delivery | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1813 |
| P05-T12-S03 | Implement mark-delivered from two sources | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1826 |
| P05-T12-S04 | Implement buyer confirm-received from all contract sources | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1846 |
| P05-T12-S05 | Test direct completion | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1859 |
| P05-T12-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1863 |
| P05-T13-S01 | Implement mark-ready-for-pickup | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1884 |
| P05-T13-S02 | Enforce no-show grace on server | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1899 |
| P05-T13-S03 | Report buyer no-show | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1912 |
| P05-T13-S04 | Contest no-show | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1925 |
| P05-T13-S05 | Late completion after contest | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1942 |
| P05-T13-S06 | Test time boundary and inventory behavior | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1953 |
| P05-T13-S07 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1957 |
| P05-T14-S01 | Implement provider mark-service-performed | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1977 |
| P05-T14-S02 | Implement buyer confirm-completed | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:1993 |
| P05-T14-S03 | Test Product-only commands are rejected on Service Orders | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2005 |
| P05-T14-S04 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2017 |
| P05-T15-S01 | Define issue report request using existing reason/text convention | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2037 |
| P05-T15-S02 | Report delivery issue | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2041 |
| P05-T15-S03 | Report service issue | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2057 |
| P05-T15-S04 | Prove issue prevents normal auto-complete | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2073 |
| P05-T15-S05 | Test duplicate issue + early issue rejection | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2077 |
| P05-T15-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2086 |
| P05-T16-S01 | Add one-row `SKIP LOCKED` candidate queries | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2108 |
| P05-T16-S02 | Process one expiry in `REQUIRES_NEW` transaction | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2163 |
| P05-T16-S03 | Process one auto-completion | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2182 |
| P05-T16-S04 | Process one issue/no-show-contest timeout | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2198 |
| P05-T16-S05 | Scheduler loops with bounded work | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2226 |
| P05-T16-S06 | Test two concurrent scheduler workers | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2240 |
| P05-T16-S07 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2250 |
| P05-T17-S01 | Expose read endpoints | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2273 |
| P05-T17-S02 | Expose all approved command routes | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2284 |
| P05-T17-S03 | Require Idempotency-Key on every state-changing Order command | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2305 |
| P05-T17-S04 | Keep `/cancel` actor-aware but unambiguous | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2335 |
| P05-T17-S05 | Test HTTP semantics | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2343 |
| P05-T17-S06 | Run and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2359 |
| P05-T18-S01 | Create explicit scenario factory | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2379 |
| P05-T18-S02 | Parameterize all allowed transitions | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2394 |
| P05-T18-S03 | Parameterize forbidden transitions | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2408 |
| P05-T18-S04 | Add concurrent actor race tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2425 |
| P05-T18-S05 | Run full Order matrix | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2441 |
| P05-T18-S06 | Commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2449 |
| P05-T19-S01 | Regenerate client from backend OpenAPI | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2477 |
| P05-T19-S02 | Render status with unknown-enum fallback | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2491 |
| P05-T19-S03 | Render buttons only from backend `allowedActions` | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2518 |
| P05-T19-S04 | Add buyer list/detail and seller list/detail pages | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2535 |
| P05-T19-S05 | Test action rendering + conflict refetch | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2539 |
| P05-T19-S06 | Run frontend checks and commit | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2548 |
| P05-T20-S01 | Verify migration chain from empty database | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2570 |
| P05-T20-S02 | Run backend Order + Inventory + Checkout regression suites | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2583 |
| P05-T20-S03 | Run full backend test suite | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2591 |
| P05-T20-S04 | Run frontend verification | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2599 |
| P05-T20-S05 | Run generated API-client drift check | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2611 |
| P05-T20-S06 | Manually inspect representative DB invariants after integration tests | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2617 |
| P05-T20-S07 | Verify required behavior checklist | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2651 |
| P05-T20-S08 | Commit any documentation-only acceptance adjustment | docs/CoVie-technical-plan-05-orders-fulfillment-v1.0.md:2682 |

Child status: NOT_VERIFIED_INDIVIDUALLY; existing module/test evidence is retained without implying each procedural step independently passed. No test rerun or source change is authorized solely by these index entries.

Inventory: 20Tasks / 122Step headings / 0explicit checklist lines selected by stated heading rule. Normative details and in-code acceptance examples remain at the linked original locations; this index is not a claim that prose/code blocks were exhaustively audited.

## Coverage and next decision

Indexed 86Task identities and625Step headings, plus37explicit checklist lines. The global W01 matrix must disclose that lower-level assertion reconciliation remains partial; no exhaustive all-project acceptance is claimed by this index. The three named Plan05 race candidates now have scoped9PASS evidence recorded above. Preserve functioning modules and all user data.
