# Phase 2 — Social interactions for catalog listings

Implementation branch: `feature/social-product-card-phase2`. Base: merged Phase 1 at `ffb23d3932382230f96382e3f269a5bf729731a5`.

## Contract (versioned /api/v1 endpoints)

- Public `GET /api/v1/catalog/social-summary?itemIds=<uuid>&itemIds=<uuid>`, 1–40 UUIDs. Returns an object keyed by listing UUID. Only ACTIVE and non-hidden catalog items appear. Each entry has `itemId, likes, comments, liked, saved`, with viewer booleans false when anonymous.
- Public `GET /api/v1/catalog/items/{itemId}/comments?limit=5&before=<uuid>` (limit 1–20, keyset pagination). Response `{ items:[{id,itemId,authorName,body,createdAt,own}], nextCursor }`.
- Authenticated PUT/DELETE `/api/v1/me/catalog/items/{itemId}/likes` and `/saves` respond with updated counts and viewer state. One like/save per user per item, idempotent transitions.
- Authenticated `POST /api/v1/me/catalog/items/{itemId}/comments` body `{body:string}` (2–1000 trimmed characters) responds 201. Global per-account 5 comments/minute with DB-backed transaction lock.
- Authenticated DELETE `/api/v1/me/catalog/items/{itemId}/comments/{commentId}` only permits the author; returns 204, repeated/foreign returns 404.

## Security and invariants

- Existing JWT session, CSRF and API error pipeline used. No anonymous mutation. No fabricated likes, saved state, comment text or success toast.
- SQL filters inactive, rejected, hidden and deleted listings from public summary and comment reads/writes.
- Comment content is plain text, React-escaped; SQL schema stores ACTIVE/HIDDEN/DELETED for moderation. Hidden/deleted comments don't contribute to counts.
- Social counts fetched in batches (up to 40/item IDs per request), only when Social mode is rendered. Thread fetch happens after user opens the panel; no per-item comment preload.
- Existing product detail routes, ads, search filters, order/checkout and catalog-only layout remain unchanged.
- Distinct from verified purchase reviews; comments must not be represented as review scores.

## Remaining production gates

- Backend and frontend unit/integration tests; CI typecheck/lint/test/build; staging UI visual QA on 360/768/1280/1440; abuse/security review.
- Comment reporting is implemented using the existing audited `/api/v1/reports` with target SOCIAL_COMMENT (migration V019); the existing admin moderation case HIDE / RESTORE actions modify individual comment visibility, with audit and outbox. Admin UI still needs staging verification.
- Social controller is now visible to OpenAPI. Generate and commit both the exported JSON and typed client with zero drift before merging.
- Do not merge/deploy until CI and manual checks complete.

## Phase 3 and release

See `phase-3-implementation-review.md`. V018 and V019 are additive, forward-only Flyway migrations. Do not drop submitted social data during a rollback.
