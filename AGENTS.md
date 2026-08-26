# CoVie project context

**Last updated:** 2026-08-26
**Repository state:** `main` at `42693a6` (`merge: close plans 01-10`), synchronized with `origin/main`.

## Read this first

This file is the operational context for humans and AI agents. Update it in the same change whenever project scope, delivery state, architecture, verification evidence, or a known gap changes. Do not infer completion from a plan title, passing unit tests, or a merged branch alone.

## Product and source of truth

CoVie is a Vietnamese local marketplace/PWA for buyers, sellers, and administrators.

- Visual source of truth: [CoVie — V1 UI Design](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=0-1).
- The Figma file records 37 production units as **FROZEN**. Figma controls visual tokens, layout, responsive behavior, and component states.
- Product/domain and operational references live in `docs/`; see `docs/CoVie-ui-handoff-matrix.md`, the technical plans, and `docs/operations/`.

### Figma page map

Use these exact page links to enter the relevant design area. A page node is navigation context only: before editing a route, select and retrieve that route's exact production frame `node-id` within the page.

| Page | Exact Figma link |
| --- | --- |
| 00 — Cover & Index | [node `0:1`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=0-1) |
| 01 — Foundations | [node `3:2`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-2) |
| 02 — Components | [node `3:3`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-3) |
| 03 — Buyer Mobile | [node `3:4`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-4) |
| 04 — Seller Mobile | [node `3:5`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-5) |
| 05 — Seller Desktop | [node `3:6`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-6) |
| 06 — Admin Desktop | [node `3:7`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-7) |
| 07 — Prototypes | [node `3:8`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-8) |
| 99 — Archive | [node `3:9`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-9) |

## Architecture

| Area | Implementation |
| --- | --- |
| Web | Next.js 16, React 19, TypeScript, pnpm workspace (`apps/web`) |
| API | Spring Boot 4, Java 25, Maven (`apps/api`) |
| Data | PostgreSQL + PostGIS, Flyway migrations |
| Supporting services | Redis and MinIO/S3-compatible media storage |
| Contracts | OpenAPI in `packages/api-client/openapi/openapi.json`; generated TypeScript client in `packages/api-client/src/generated/` |
| Operations | Docker Compose, GitHub Actions, deployment/rollback, backup, observability, security and release runbooks under `infra/` and `docs/operations/` |

## Integrated product scope

The following domain capabilities are present in `main` and have automated verification coverage:

1. Repository foundation, migrations, shared error/idempotency/outbox conventions.
2. Identity, password/OAuth flows, sessions, user profile and addresses.
3. Merchant, location, catalog, public discovery and media foundations.
4. Cart, shipping, inventory reservations, checkout preview/commit.
5. Order creation, authorization, lifecycle, cancellation, fulfillment, issues and read models.
6. Buyer need requests, merchant matching/responses and need-sourced checkout.
7. Reviews, reputation/trust projections, notifications and SSE invalidation.
8. Moderation, reports, administration, settings and audit operations.
9. Web routes, API-client consumption, functional route flows, unit/E2E harness.
10. Production-oriented Docker/CI, deploy/rollback, backup/restore, observability, security and release artifacts.

## Current delivery status — do not overstate

| Area | Status | Evidence / limitation |
| --- | --- | --- |
| Backend/domain implementation | Integrated and verified locally | `mvnw clean verify`: 460 tests passed, 1 skipped (2026-08-26). The regression includes buyer/merchant first-page order queries without nullable cursor parameters. |
| Web functional implementation | Integrated and verified locally | API client/typecheck, 97 Vitest tests and production build pass; standard Playwright has 2 passed and 1 intentional local-fixture skip; populated Public Buyer P0 Playwright has 1 passed (2026-08-26). |
| Git integration | Complete | Plan branches/worktrees were merged and cleaned; only `main` remains. |
| Figma UI fidelity | **In progress; not complete** | The requested Public Buyer P0 chain has populated local acceptance for PUB-001, PUB-003…008 and BUY-001/002/005/006 against exact frozen frames at 390×844, 360×800 and 1280×900. The PUB-009 Shop variant has a contract-safe first pass and viewport review; the Provider variant and contract-unavailable catalog/capability details remain open. Other routes remain scaffold/open unless recorded in the handoff matrix. |
| Staging/release rehearsal | **Not complete** | No real staging environment, production credentials, or deployment evidence is available. No staging or production deployment has been performed. |
| Lint | Non-blocking warnings remain | Two existing Next.js warnings remain (`catalog-card` image and catalog-create navigation); no lint errors. |

## Known UI gap

PUB-001, PUB-003…008 and BUY-001/002/005/006 now use the frozen Inter/Emerald foundation, exact exported SVG assets and buyer navigation. Search covers URL filters, FilterSheet and category states, including the queryless-category sort adapter required to avoid backend 422. Product and Service detail use real offer availability/price; Product Detail links its contract-backed merchant to the PUB-009 profile. Service selection uses native radios, always submits quantity one and explains V1 coordination without inventing calendar booking. Resale detail uses the exact media/listing/card/action anatomy, always submits one unit, and does not invent the sample condition percentage or pickup/delivery eligibility absent from the public contract. The PUB-009 Shop first pass renders public merchant identity, reputation, reviews, safe contacts and locations; its Provider discriminator, fulfillment/open cues and public catalog cards are unavailable in current public contracts and remain open. Cart/checkout preserve separate Product/Service quantities, `If-Match` and checkout-token conflict recovery, backend-only totals and truthful order-created semantics. `pnpm ui:verify-local` creates a deterministic ignored fixture and verifies the real Home → Search → Product Detail → Shop Profile → Service Detail → Resale Detail → Cart → Checkout → Success → Orders journey, while writing viewport comparisons to `.local/dev/visual-review/`. Exact type-specific wording in route-level `loading.tsx`/`error.tsx` remains constrained because Next.js supplies no route params to `loading.tsx`; see the handoff matrix. Remaining routes are functional scaffolds unless recorded otherwise.

Before changing a UI route:

1. Obtain the exact Figma frame URL including `node-id`.
2. Read its design context and map it to existing project components/tokens.
3. Implement loading, empty, error, unauthorized, desktop and mobile states.
4. Compare a rendered route with the Figma frame and record the review.
5. Only then mark that route visually complete.

## Local development

After the one-time `.env.local` and disposable JWT-key setup, start the complete local stack with:

```powershell
pnpm dev
```

This starts PostgreSQL, Redis, MinIO, the API and web app. Stop it with `pnpm dev:stop`. Logs are ignored under `.local/dev/`.

The launcher runs Maven Wrapper from `apps/api` so its `.mvn/wrapper` files resolve correctly. If `JAVA_HOME` is unset but `java` is available on `PATH`, it derives `JAVA_HOME` from that JDK automatically; otherwise it reports the required JDK setup explicitly. The regression check is `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1`.

The default port `8080` is occupied by an environment service in this workspace. The launcher runs API on `8081` and web on `3000`.

```powershell
http://localhost:3000
```

Required API local configuration includes PostgreSQL (`5433`), Redis (`6379`), MinIO (`9000`) and disposable local JWT PEM files in ignored `infra/secrets/dev/`. Generate missing local keys with `infra/scripts/generate-dev-jwt-keys.sh`; never commit PEM files or real OAuth credentials.

For repeatable populated Public Buyer P0 acceptance, run `pnpm ui:verify-local`. It seeds only deterministic local fixture records, stores the generated buyer credential in ignored `.local/dev/ui-fixture.json`, and writes screenshots under ignored `.local/dev/visual-review/`.

## Verification commands

```powershell
# API
Set-Location apps/api
./mvnw clean verify

# From repository root
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e
pnpm ui:verify-local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1
```

## Documentation maintenance checklist

- Update this file for every material implementation, merge, rollback, environment change, verification result, or known limitation.
- Update the matching technical plan and runbook when behavior or operations change.
- Record the exact Figma frame and visual-review evidence when a UI screen is completed.
- Keep generated OpenAPI and API client synchronized after API contract changes.

## Required AI progress response

During work, do not stop at a diagnostic report. Every substantive response to the user must end with these explicit sections, in this order:

1. **Next action:** the single concrete action the AI will take next. Continue it without waiting when it is read-only or already authorized.
2. **Required from you:** only the exact decision, credential, access, URL, asset or approval that blocks that action; write `Nothing` when no user input is needed.
3. **Completion condition:** the observable result that will allow the AI to move to the following action.

If an input is missing, explain why it is required, give the shortest actionable way to provide it, and stop only when the missing input materially blocks safe progress. Never leave the user to infer the next step from a long status report.
