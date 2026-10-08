# CoVie advertising engine

User-approved scope: both stages of the banner proposal, including merchant campaigns and moderation. No payment, bidding or billing is included.

## Management

Admin: `/admin/ads`. Seller: `/seller/[merchantId]/ads`. Desktop navigation includes Ads; the frozen five mobile seller tabs remain intact, with a separate Ads header link.

Campaigns contain title, body, CTA, destination, creative type, template/media, placement, device, UTC start/end, enabled flag and priority. Admin campaigns are approved on creation. Merchant campaigns start DRAFT, submit to PENDING, and require Admin approval. Merchant edits return to DRAFT and stop serving until resubmitted/approved. Rejection requires a reason. UI buttons consume server `allowedActions`; all mutation authorization remains on the server. A version/`If-Match` prevents overwriting another editor. Management lists append 100-row pages with retry/deduplication.

Seller destinations must identify their own `/merchants/{id}` or `/items/{id}`. External destinations are Admin-only HTTPS URLs. Credentials, protocol-relative links, backslashes, control characters and non-HTTPS schemes are rejected. Hidden/deleted internal targets do not serve. Uploaded media must be READY, purpose AD_BANNER, owned by the current uploader; an unchanged existing attachment retains its original uploader.

## Placement and creatives

| Placement | Current host | Responsive behavior |
| --- | --- | --- |
| SIDEBAR_LEFT | Home and search/category results | 160×600 outside the content gutter; viewport >=1440px, height >=780px and measured left gutter >=200px. Sticky at 96px. Never shrinks product content. |
| HOME_HERO | Home before catalog sections | Horizontal, filtered by selected device. |
| IN_FEED | Home between sections; search after the first six results, or after the last result for smaller pages | Horizontal inside the content column. |
| ITEM_DETAIL | Product/service/resale detail, below primary details/actions and before reviews | Horizontal; purchase controls retain their own layout. |

There are no ad slots in checkout, carts, orders, profiles, authentication, public merchant profiles, Admin or Seller workspaces. No eligible ad means no ad content/blank reserved height. Placement and device are separate campaign settings; campaign copies do not invent ratings, discounts or inventory.

Image formats: WebP/JPEG/PNG, existing image bound (10MiB default). MP4/WebM: AD_BANNER only, default maximum 50MiB; container signature, MIME and stored byte size verified on completion. Existing image consumers retain their image-only contract. READY assets are not removed by pending-upload cleanup.

CSS creative templates FLASH/NEARBY/SPOTLIGHT/HERO adapt the presentation branch without switching/merging the dirty checkout. Phosphor icons replace template Unicode art. Motion pauses for reduced motion, hidden pages or offscreen delivery. Video is muted, inline, has native controls and only attempts play while visible. Rotation changes every 10 seconds and offers previous/next/pause; focus/hover pauses automatic rotation. Explicit `Quảng cáo` disclosure remains visible.

## Delivery and tracking

- Public `GET /api/v1/ads/active?placement=…&device=DESKTOP|MOBILE` is no-store. It filters enabled/approved, inclusive start/exclusive end, device, target/media visibility and descending priority. It returns at most 10 eligible campaigns from a bounded candidate query.
- Server creates a persisted opaque delivery UUID with campaign version and 30-minute expiry. No owner/review metadata is public. Tokens remain valid across API replicas/restarts sharing the DB.
- UI refreshes configuration every 60 seconds while visible and removes campaign/token expirations immediately. Actual visibility requires at least 50% intersecting for a continuous second with the tab visible. Hidden tabs reset that interval; failed media cancels it. Carousel revisits do not resend that delivery impression.
- `POST /api/v1/ads/{id}/events` receives `{deliveryToken,eventId,type,visibleMillis}`. Beacon sends immediately on click; keepalive fetch is the fallback. Navigation does not wait. Database uniqueness on canonical delivery/type and event UUID prevents replay, including UUID case variants. Expired/version-changed/disabled/deleted campaigns reject events.
- Only that exact telemetry POST is exempt from browser CSRF because its expiring delivery proof authorizes anonymous aggregate telemetry; campaign management and all other business writes keep existing CSRF protection. Public discovery rate limiting applies. These counts are aggregate engagement metrics, not billable fraud-proof conversion attribution.

## API operations

Admin base `/api/v1/admin/ads`; merchant base `/api/v1/merchants/{merchantId}/ads`:

- GET base: `offset` 0..1,000,000, 100 records/page.
- POST base: create.
- PATCH/DELETE `/{id}`: edit/soft-delete with `If-Match`.
- POST Admin `/{id}/review`: approve or reject with reason and `If-Match`.
- POST merchant `/{id}/submit`: submit with `If-Match`.
- GET `/{id}/stats?from=YYYY-MM-DD&to=YYYY-MM-DD`: up to 93 inclusive UTC days.

Stats totals include impressions, clicks and percent CTR (`clicks / impressions × 100`, zero when impressions zero). The UI refreshes every 30 seconds while visible, rejects invalid date ranges and ignores obsolete queries. Two-series line chart fills zero-event dates and has a matching accessible table.

Migration: `V015__advertising.sql` adds campaigns, expiring deliveries, deduplicated events and indexes. Source OpenAPI and generated client are synchronized. No migration of existing business data is required.

## Local verification — 2026-10-08

- Final focused backend gate: 17 tests passed (12 ads tests, 4 existing cookie/CSRF tests, 1 OpenAPI export), on disposable PostgreSQL/PostGIS with all 15 migrations.
- Media integration: 9 tests passed, including MP4 completion and mismatched WebM failure cleanup; isolated test storage/container.
- Full web: 103 files / 409 tests passed. API-client check, web TypeScript, zero-warning ESLint and production build (36 generated pages) passed.
- Chrome public ad fixtures: three tests at 390×844, 1366×768, 1920×1080, home/search, real scroll bounds, continuous visibility, rotation/pause and beacon click during external fixture navigation. Campaign API requests are intercepted; no actual campaign writes. Evidence `.local/ad-engine/browser/`.
- Existing public Figma contexts retrieved: Home282:290, Results294:1289, Product299:2562, Service304:2803. Existing Admin/Seller shell contexts retrieved by management worker. New Ads screens/creatives are the approved feature extension; no frozen Ads frame is claimed.
- Protected Chrome management fixtures passed for Admin/Seller at 390 and 1366 px: real demo login, intercepted campaign create/editing actions, submit/approve, statistics and deletion. Credentials are selected with `COVIE_AD_LOGIN_FIXTURE`; the old `.local/dev/ui-fixture.json` belongs to another database. Current recovered demo accounts are recorded privately in `.local/ad-engine/recovered-login.json`. No password reset/reseed was performed.
- Full backend rerun: 566 tests, zero failures/errors, one skipped. Used the existing `covie.test.minio-image` override with cached Chainguard MinIO because the default registry rejected pulls. An initial checkout rollback failure did not repeat on the full rerun; no checkout source was changed.
- Local API restarted against the same `covie_demo_recovered` database; Flyway V015 succeeded. Backup: `.local/ad-engine/covie-before-ads.dump`. Real API verification passed: Admin/Seller login, seller create/submit, admin approval, seller edit invalidating approval, statistics, deletion and public discovery. Temporary campaigns are disabled and soft-deleted; existing business rows/media were retained.
- Runtime verification caught and fixed the frontend ETag contract: all mutations send `If-Match: "vN"`, matching the shared backend parser. API regression and browser fixture assertions enforce this format.

Local API is running the new implementation on port 8081; web remains at localhost:3000. Real local campaign lifecycle was verified through the API, while browser campaign operations use isolated fixtures. Real local video uploads and staging/production deployment remain unverified. Media verification uses isolated integration storage.

## Commands

```powershell
pnpm api-client:generate
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
$env:COVIE_AD_FIXTURE='1'
pnpm --filter @covie/web exec playwright test e2e/ad-engine.fixture.spec.ts --workers=1
# Select the recovered database's private local login fixture:
$env:COVIE_AD_LOGIN_FIXTURE='E:\WebApp\CoVie\covie\.local\ad-engine\recovered-login.json'
$env:COVIE_AD_MANAGEMENT_FIXTURE='1'
pnpm --filter @covie/web exec playwright test e2e/ad-management.fixture.spec.ts --workers=1
```
