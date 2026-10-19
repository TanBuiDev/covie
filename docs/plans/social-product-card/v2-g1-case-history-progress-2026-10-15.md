# Social Product Card 2.0 — G1 case history

Base/working HEAD: `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`, uncommitted local
work. This extends the gated G1 reads; no existing moderation command was changed.

## Implemented

- Detail retains its first 50 cases and total; adds optional
  `moderationCaseNextCursor` when older cases remain.
- New read endpoint:
  `GET /api/v1/admin/catalog-items/{itemId}/moderation-cases?limit=50&cursor=...`.
  Limit 1–100; default 50, bounded input cursor (1024 characters), private/no-store,
  AdminAccessPolicy and the same default-OFF Admin read flag.
- Response: items, hasMore, optional nextCursor and total. Only cases with the
  requested target_catalog_item_id are read. Unknown item is 404; malformed or
  cross-item cursor is 422. Guest/non-Admin remain 401/403.
- Opaque base64url/versioned cursor includes item ID, openedAt and case ID. SQL
  seeks strictly below `(opened_at,id)` descending; no offset or lookup of a mutable
  anchor row. Newer reports do not shift continuation; timestamp ties use UUID.
- The cursor is a navigation token, not a secret or a signed authorization token.
  Admin authorization is checked on every request independently of cursor data.
- UI appends/deduplicates case rows, retains existing links through network failure
  and retries the same cursor. Responses for a different detail key are ignored;
  401/403 during case loading clear the detail. Terminal pages remove Load More.
- Full refresh reconciles newer cases. The total may increase while an anchored
  history is being browsed; this does not insert new cases into older pages.

## Verification

| Check | Evidence |
| --- | --- |
| Backend RED | `.local/social-v2-g1-case-history-red.log`: one failure, missing detail cursor |
| Backend + export GREEN | `.local/social-v2-g1-case-history-green.log`: 6 tests pass, zero failures/errors/skips, 41.869 s |
| UI RED | `.local/social-v2-g1-case-history-ui-red.log`: missing case continuation control, other 4 tests pass |
| UI GREEN | `.local/social-v2-g1-case-history-ui-green.log`: 5 tests pass, 6.24 s |
| Client generation/typecheck | `pnpm api-client:generate`, `pnpm api-client:check`: exit 0 |
| Web typecheck | `.local/social-v2-g1-case-history-typecheck.log`: exit 0 |
| Targeted lint | `.local/social-v2-g1-case-history-lint.log`: exit 0 |
| Contract compatibility | Recursive semantic comparison against `.local/social-openapi-20261015.json`: pre-V2 routes/schemas unchanged; V2 adds the three Admin routes and additive DTOs |

The PostgreSQL/JWT/HTTP regression uses 52 real persisted cases at one timestamp,
verifies the first 50 and remaining two in deterministic order, inserts a newer
case between reads, excludes another item's case and rejects cross-item/invalid
cursors. Existing case rows compare unchanged after reads. This is a read-model
fixture test, not proof of the report-write/audit/outbox chain.

UI tests mock transport/navigation and verify 50+1 case append and retry without
losing links. They do not substitute for real browser/staging evidence.

Tests ran in `.local/social-v2-api-validation/api` with disposable PostgreSQL,
avoiding the running application's build directory and recovered database.
No migration/index or runtime restart, seed, PR merge or deployment was performed.

## Remaining G1 / full objective

The prior 50-case browsing limitation is resolved. G1 still requires real
report→case→HIDE→audit/outbox→public visibility→RESTORE evidence connected to these
reads/UI, query-plan validation and real responsive/browser comparison. Admin case
creation for unreported content needs its audited contract rather than direct SQL
mutation. Storage privacy and actual staging/load/recovery/release gates remain
unverified. G2–G6 and F01–F07 are not complete; the full objective stays active.
