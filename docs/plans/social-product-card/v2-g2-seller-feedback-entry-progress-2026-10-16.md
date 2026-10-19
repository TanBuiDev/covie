# Social V2 — Seller feedback entry and moderation reason audit

## Findings from current routes/contracts

Plan G2.3 requires an entry to the item's comments and real moderation status/reason according to permission. The actual Seller edit route only mounts `CatalogEditForm`; neither that form nor the public `CatalogDetail` had a Social comment thread/entry. Linking to an invented public `#comments` target would therefore not satisfy the requirement. Linking a DRAFT/hidden item to the public detail can also lead to the existing legitimate404.

The existing reusable `SocialComments` already consumes paged public comments through the CSRF-aware adapter and supports close/Escape, error/retry, author delete and report. Backend visibility/ownership rules remain authoritative; Seller editor access does not grant access to hidden/deleted comments.

Moderation reason is a separate real gap: `CatalogItemManagementDetailResponse` contains moderationStatus but no decision reason; the Seller route has no other source. `ModerationOutboxHandler` sends OWNER/MANAGER only a generic notification with targetType/targetId, not a reason or note. Admin moderation endpoints contain privileged case/action data and must not be consumed directly by Seller to fill this gap.

## Minimal comment entry implementation

- New `SellerSocialComments` reuses existing Seller panel/button/icon styling and the existing SocialComments component/API.
- Mounts only inside the existing Social authoring flag-ON edit form, including the read-only editor view. No comment data is requested before the user opens it.
- Opens the item's existing thread inline, not an aggregate dashboard, new route or fabricated public deep-link. It explains that data follows the item's current visibility policy; unavailable/unpublished errors do not masquerade as an empty comment list.
- Close/Escape unmounts the thread and restores focus to its entry. It performs no Catalog submit/refresh/navigation; unsaved caption stays intact. The child is keyed to item identity.
- No new BE route, mutation permission, comment-status policy, API/client/schema, CSS design or moderation transition. Existing thread create/delete/report controls retain existing server authorization.
- Flag OFF leaves the old form unchanged. Create form has no persisted item ID/thread and gains no fake entry.

## Targeted evidence

- Missing-module RED: `.local/social-v2-g2-seller-comments-red.log`. New test could not import the absent component; no claim that this was a failing existing API.
- `.local/social-v2-g2-seller-comments-green.log`: **34 PASS**, two files, 8.38s. Covers lazy request, successful literal comment text (no script element), close/Escape/focus, unavailable404 error without fabricated comments/Admin/public links, flag ON/OFF entry and existing editor regressions.
- Final additional actual-editor draft case: `.local/social-v2-g2-seller-comments-draft.log`, **1 PASS**, 32 skipped, 4.51s. Change the actual Caption field, open existing thread returning404, close, verify caption/focus retained and zero Catalog update/router refresh. Only test mocks changed after the preceding run; unchanged production/component cases were not repeated.
- Web typecheck exits0: `.local/social-v2-g2-seller-comments-typecheck.log`.
- Targeted ESLint exits0: `.local/social-v2-g2-seller-comments-lint.log`, one existing legacy img warning in edit form; no new error. Diff check passes.
- Component transport is mocked, not a rendered Next/Seller/API browser chain or four-viewport/a11y acceptance. No BE tests/client generation/full suite rerun.
- Normal web/API PIDs7544/25008 remain alive. No runtime/recovered DB access, restart, activation, branch/commit/remote operation or deploy. Whole V2 ACTIVE; normal V2/video flags OFF.

## Next bounded BE contract work

The approved plan requires a real Seller-visible reason, not disclosure of Admin notes. The minimal proposed implementation for the next slice is an optional reason-code-only field on the existing authenticated Seller detail:

- Use the existing OWNER/MANAGER recipient scope established by ModerationOutboxHandler, active membership/merchant/item scope checks; do not expose it to STAFF simply because that role can read the editor.
- For current item HIDDEN/REJECTED, resolve the latest matching actual HIDE/REJECT decision reasonCode on the same catalog target. For other states or no matching decision, return no reason. Do not substitute user-report reason/note or an old restored decision for a current reason.
- Do not return Admin note, reporter identity, action/case internals, audit/outbox or unrelated target data. Plain literal rendering only.
- Keep new read behavior behind the existing V2 authoring gate; preserve flag-OFF/old contract fields, with optional additive OpenAPI/client generation.
- First prove missing reason with issued-JWT HTTP plus denied scopes/readonly snapshots; then add the minimum bounded query and UI consuming the generated field. Do not add a migration/index without measured evidence or ask again for the plan's already granted approvals.

This is technical implementation planning within the approved requirement, not evidence that the reason contract is already implemented. Its privacy/current-state tests and real Seller browser acceptance remain open.
