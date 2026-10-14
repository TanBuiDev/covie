# CoVie Social Product Card — Phase 3 implementation and joint acceptance

PR: https://github.com/TanBuiDev/covie/pull/28
Branch: `feature/social-product-card-phase2` (combines Phase 2 + Phase 3 for one controlled merge).

## Delivered Phase 3 code

- Shared server-confirmed social summaries across duplicate cards in independent home sections, without storing viewer flags in persistent browser storage.
- Batch summary reads (max 40 item IDs), no per-card N+1 fetch, and throttled reconciliation after focus/online/visibility restoration.
- Per-item mutation serialization so concurrent Like/Save responses cannot overwrite another confirmed whole summary.
- Lazy comment fetch immediately on opening a thread; loading, error, retry, empty state, pagination and count UX.
- Escape to close, keyboard focus on Close, touch-sized actions, character count and retained comment draft after failure.
- Login prompts and differentiated 403/404/429 failure messages; no optimistic success claims.
- Comment report calls reuse existing audit/idempotency/CSRF moderation endpoint. Social comments remain distinct from purchase reviews.
- Home, search, Catalog mode, location deep links, product detail, ads and checkout pathways have not been intentionally changed.

## Concurrent Phase 2 hardening

- V018 reaction uniqueness and comment visibility; V019 links comments to the existing moderation case pipeline.
- Public social API rejects hidden/inactive catalog items and hidden/suspended merchants.
- Existing admin moderation HIDE/RESTORE applies to individual comments, with audit/outbox and author notification.
- OpenAPI export + generated typed client must have zero drift.

## Merge gate — all mandatory

1. No conflicts with current main; latest PR head reviewed.
2. All required CI checks PASS on the same head: Maven verify, OpenAPI typed generation and no drift, web typecheck/lint/test/build, secret-history, security scanner, CodeQL.
3. Responsive browser QA at widths 360/768/1280/1440 in a running staging/local app, including long names, pagination, slow/offline failures, keyboard, ads, mobile and Catalog toggling.
4. Manual auth + CSRF matrix: anonymous reads; no anonymous writes; suspended users/merchants; deleted/hidden content; comment ownership; admin-only actions.
5. Verify report -> moderator HIDE/RESTORE -> audit -> public counter/read projection, while unaffected items remain visible.
6. Confirm no regression in checkout, cart, search and merchant detail; review production release and additive-schema rollback (do not drop migrated tables).
7. Do not merge or deploy if any gate lacks evidence, even if all CI checks pass.

## Rollback

Application rollback should revert the code without dropping V018/V019 tables or user comments. The Flyway migrations are additive and forward-only.

## Evidence note

The 702-line v1.0 implementation plan was not available as an attached file or project-backed source during this pass. This review uses the phase outline in the conversation and the inspected repository contracts; no unknown requirement is presumed complete.
