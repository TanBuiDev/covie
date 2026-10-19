# Social Product Card 2.0 — G4 private current-state activity backend

## Contract implemented locally

Added `GET /api/v1/me/social/activity`, independently guarded by default-OFF
`covie.social-v2.activity-enabled`. It requires authentication and the `type`
query: `LIKED`, `SAVED` or `COMMENTED`. Limit defaults to 20, accepts 1–50;
optional `cursor` is bounded to 1,024 characters and invalid input returns 422.
Success responses use `Cache-Control: private, no-store`.

Actor is taken exclusively from the existing authenticated actor provider. There
is no client-controlled user selector. An unexpected `userId` query parameter
does not change ownership filtering. No new role, scope or alternate authorization
path was introduced.

Semantics follow the approved plan, not an append-only audit history:

- LIKED/SAVED read the actor's still-existing LIKE/SAVE rows. Unlike/Unsave remove
  the corresponding activity.
- COMMENTED reads each ACTIVE authored comment, allowing multiple rows per item.
  A deleted/hidden comment is excluded by the SQL status predicate.
- All branches apply the same active, undeleted item/merchant and public
  moderation predicate as existing Social reads. Invisible entries are omitted
  rather than exposing their status or a special investigation reason.
- Response entries expose item/name/merchant, activity timestamp, optional cover,
  optional comment ID/text and `itemAvailable=true` for the visible rows returned.
  `commentExcerpt` currently carries the existing bounded comment body (maximum
  1,000 by the current write contract), not a new backend truncation policy.

The actor/type-scoped descending keyset is `(created_at, catalog_item_id)` for
reactions and `(created_at, comment_id)` for comments. Reaction item ID is unique
within the actor/kind primary key. Versioned base64url JSON cursor binds actor,
type, timestamp and tie-breaker; a deleted cursor-anchor row need not be loaded.
This is an opaque continuation encoding following repository conventions, not
a signed token or authorization credential. Ownership is always enforced in SQL.

One bounded data query selects limit+1 entries and an eligible cover through a
lateral join. At most 50 distinct READY, nondeleted CATALOG_IMAGE assets are
materialized in a second batch, using the existing URL resolver. Pending primary
media is skipped in favor of the first eligible image. No per-entry detail calls,
new mutation, audit-event table, index or schema migration was added.

## Evidence

`.local/social-v2-g4-activity-red.log`: **2 missing-route failures**, expected 200
but received 404, before implementation.

`.local/social-v2-g4-activity-green.log`: **5 tests pass, 31.634 s** (two new
activity HTTP/PostgreSQL tests plus the then-existing three OpenAPI tests):

- Real issued owner JWT: existing Like/Save mutations appear in private lists;
  Unlike/Unsave disappear. Two real comment POSTs produce two activity entries;
  deleting one removes only that entry; hiding the item removes the remaining
  comment activity. Literal comment markup remains unchanged text in JSON.
- Another issued user sees no owner activity; anonymous request is 401.
- Twenty-five reaction rows sharing a timestamp page as 20+5 with no duplicate
  item IDs. Reusing a cursor for another type or actor is 422. Limits 0/51 and a
  malformed cursor are rejected. Reaction row snapshots stay unchanged by reads.

`.local/social-v2-g4-activity-private-final.log`: **2 focused tests pass,
27.198 s**, extending only relevant proof:

- READY cover fallback behind pending primary, exact trusted URL, no storage-key
  property, and visible availability. Client `userId` cannot read another user's
  activity.
- Default-OFF authenticated request is 404; unauthenticated request remains 401.

An actual generated-contract assertion found `limit` was mistakenly exported as
a string with default `"20"`. Adding integer/int32 metadata only to the new
parameter fixed it. `.local/social-v2-g4-activity-schema-final.log`: **4 OpenAPI
tests pass, 28.545 s**, including the new integer/default/bounds/type-required
assertion and existing media-schema separation/export checks.

Client generation/check and web typecheck passed:
`.local/social-v2-g4-activity-client-generate.log`,
`.local/social-v2-g4-activity-client-check.log`,
`.local/social-v2-g4-activity-web-typecheck.log`. Semantic comparison confirms
all 142 pre-V2 paths and 200 schemas unchanged except the four previously approved
optional caption fields. No existing Social mutation implementation was changed
in this checkpoint; earlier dirty changes remain preserved.

Tests ran sequentially in the isolated API copy with disposable PostgreSQL.
No whole backend suite, recovered-data write, original runtime restart, feature
activation, commit or deployment occurred.

## Remaining gates

G4 is not complete. The suspended/rejected/deleted item/merchant and hidden
comment matrix needs targeted HTTP proof beyond the current hidden-item case.
Comment cursor ties, concurrent changes/reaction recreation, representative
SQL plans/query counts and load evidence are also unverified. Existing V018
indexes are retained until measurement justifies a change. No dedicated private
read rate-limit group was invented; current-state route protection and any
activation traffic limits need verification under the existing system policy.

The `/me/activity` UI/profile entry, URL tabs, append/error/retry/auth transitions
and literal excerpt rendering have not been implemented. No existing public
feed or profile screen was modified. Remaining G1–G3 technical/browser gates,
G5 video and G6 release evidence also remain open. No staging exists; all new
flags default OFF and release is unverified. The full objective stays active.
