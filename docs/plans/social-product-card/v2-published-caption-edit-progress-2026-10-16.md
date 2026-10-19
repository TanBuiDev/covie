# Social V2 — published caption edit / ETag / Admin visibility

Date: 2026-10-16. Scope: plan11.3.5, local only. Owner Figma waiver persists; current project UI is authoritative. No production application change.

## Source contract and focused fixture

`CatalogEditService.updateItem` already applies the existing policy when caption/media changes on published ACTIVE/PAUSED content. PRE_APPROVAL changes moderation to PENDING and opens/reuses an OPEN PRE_APPROVAL case. `VersionPrecondition.require` and existing controller tests specify **409 RESOURCE_VERSION_CONFLICT**, not an invented412 contract. Admin's existing flag-guarded detail reads the current caption and case. Preserve all three behaviors.

Added optional `covie.test.social-published-edit-browser=true` branch to existing `CatalogSocialNavigationBrowserIntegrationTest`, plus `apps/web/e2e/seller-published-edit-browser.mjs`. Only this branch enables isolated Admin reads in API/Next. Fixture starts one ACTIVE/APPROVED PRODUCT with caption, commerce description/default variant/offer, OWNER and Admin identities, and an isolated merchant PRE_APPROVAL setting. No live policy change. This is an edit-focused baseline, not another create/publish proof. Existing navigation/feedback/offer branches retain their assertions.

Actual Next/API/disposable PostgreSQL and issued HttpOnly OWNER/Admin cookies, Chrome155.0.8059.84 at1280:

1. Two native Seller tabs load the same persisted caption/version0.
2. First native save sends exactly `{socialCaption}` with normal CSRF and If-Match `"v0"`, receives204/ETag`"v1"`. Router refresh shows accepted literal multiline caption and “Đang chờ kiểm duyệt”.
3. Second tab submits its stale caption with original If-Match`"v0"`, receives409 RESOURCE_VERSION_CONFLICT. Native conflict message appears and save becomes disabled. Accepted edit is not overwritten.
4. Independent Admin-cookie context opens actual `/admin/listings?item=<id>`: private/no-store detail200 has PENDING, new caption and one case. Native UI renders literal `<script>` text, no script element, unchanged commerce description and exact same-case link. Two dev detail reads both200/PENDING. Admin performs no write.
5. Java independently checks caption/version1/PENDING and full item JSONB excluding only caption/moderation/time/version. Twelve protected tables remain full-JSONB unchanged: variants, offers, gallery associations, merchant/profile/membership/location/category/assets/reactions, comments, moderation actions and audit. Exactly one OPEN PRE_APPROVAL case targets this item and exactly one MODERATION_CASE_OPENED outbox record exists; no other case/outbox. Owner media count0. Evidence pageErrors must be an array of size0 after context cleanup.

No media is uploaded/changed in this caption-only checkpoint; it is not new MinIO/object-access evidence. Previous all-three-type actual upload/publish/feed proof remains separate and is not rerun.

## Verification and boundaries

Final **1PASS63.09s**, Maven **01:15min**,0skip: `.local/social-v2-published-edit-browser-final.log`, evidence directory of same basename. Exactly2 CSRF PATCHes (204 then409), Admin writes0, pageErrors[]. Admin1280 screenshot inspected contextually; not four-width/a11y/Figma acceptance. Final targeted ESLint and node syntax checks PASS; focused whitespace check PASS.

Earlier failures were test construction only: duplicate local Java variable prevented test compilation; the first runnable helper incorrectly expected412 despite the existing409 contract. The missing opt-in Admin-read flags were corrected before the final run. These are not product RED evidence, and no application/API change was made in response. Initial root-CWD ESLint invocation could not find config; rerun from `apps/web` exited0.

Five relevant production source hashes match isolated validation copies (EditService, AdminCatalogReadService, VersionPrecondition, Seller form, Admin screen); Java fixture also matches. Fixture manifest deleted and owned API/Next processes shut down; normal3000/8081 absent/untouched. No full suites, old successful navigation/gallery/creation/race flows, typecheck/build, API/client/schema/migration/CSS change, recovered/runtime DB access, normal restart, remote operation, activation or deploy. Normal V2/video flags stay OFF, whole objective ACTIVE.

Local11.3.5 scoped published edit/ETag/PRE_APPROVAL/Admin-read chain now verified. Not staging, native Admin approve/HIDE/RESTORE, public visibility after those actions, every policy/type/role, login-form, media edit, load/CDN or security-matrix proof. Do not repeat unchanged successful paths or treat all caveats as new mandatory gates.

Next action: Trace existing native Admin HIDE/RESTORE controls and fixture effects for the still-required11.3.6 chain.
Required from you: Nothing
Completion condition: Identify one scoped actual Admin UI→public visibility→audit/outbox verification without replacing native Admin commands with Node bearer setup or repeating prior happy paths.
