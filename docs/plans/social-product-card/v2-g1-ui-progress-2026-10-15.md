# Social Product Card 2.0 — G1 Admin UI progress

Base/working HEAD: `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`, uncommitted local
work. This is progress toward G1, not a complete V2 or staging acceptance claim.

## Implemented

- `/admin/listings` renders the real Admin catalog UI only when server environment
  `COVIE_SOCIAL_V2_ADMIN_READS_ENABLED=true`. Default OFF retains the existing
  moderation navigation screen, with copy updated to describe the disabled feature.
  Backend `covie.social-v2.admin-reads-enabled` must also be enabled independently
  in an isolated verification environment; client visibility does not grant access.
- API adapter consumes generated components types and the existing authenticated,
  no-store browser request pipeline. It checks list/detail payloads before use.
- Search, existing type/listingKind/status/moderation/merchant filters and allowed
  sorting are encoded in URL query parameters. Selecting a row preserves filters
  and stores its item ID in `item`; closing returns to the filtered list.
- Load More appends and deduplicates rows without navigation or losing prior
  content. A confirmed retry defect was reproduced and fixed: retry after a failed
  next-page request now retries that page instead of resetting to page zero.
- Detail shows authoritative description, ordered media with failed-image fallback,
  location state and real case IDs/status/reason. Links open the existing moderation
  detail screen, leaving command validation/audit/outbox with that implementation.
- No-case items explicitly say there is no case. No fake case ID, direct SQL action
  or inferred HIDE/RESTORE permission was added.
- Loading, empty, 401/403/404/429/error and retry states are present. Old data is not
  rendered as the result of a newly selected filter/detail request; stale responses
  are ignored. Permission failure during pagination clears listed rows.

## Design and verification

The exact Figma production frame `399:1413` (ADM-005 Listings) was retrieved with
high-fidelity context and screenshot in the preceding backend pass. Implementation
reuses AdminPageHeader, AdminSurface, AdminStatusChip, current Admin button/field
styles, Phosphor icons and page shell. Content/cards remain white. New module CSS
has 1180px/600px responsive breakpoints; the table scrolls within its own container.
The approved V2 filters/media/detail extend the older frame's basic listing layout.

| Check | Result / evidence |
| --- | --- |
| Missing component RED | `.local/social-v2-g1-ui-red.log`: import failure before component existed |
| Initial component suite | `.local/social-v2-g1-ui-green.log`: 3 pass |
| Retry regression RED | `.local/social-v2-g1-ui-retry-red.log`: next-page retry test fails; other 3 pass |
| Final component suite | `.local/social-v2-g1-ui-final.log`: 4 pass, 6.07 s |
| Typecheck | `.local/social-v2-g1-ui-typecheck-final.log`: exit 0 |
| Targeted ESLint | `.local/social-v2-g1-ui-lint-final.log`: exit 0 |
| Working diff whitespace | `git diff --check`: exit 0 |

Component tests mock API transport/navigation to verify UI behavior; they are not
real authenticated browser/E2E or staging evidence. No full backend rerun was done:
this pass did not change backend code or API schemas.

## Remaining G1 acceptance

- Real browser integration at 360/768/1280/1440 and visual comparison remains
  UNVERIFIED. Existing local API/Web processes occupy 8081/3000 and were preserved;
  no restart against the recovered database, environment rewrite or seed was done.
- The backend detail provides the latest 50 item cases with a total, and the UI
  explicitly reports truncation. Complete item-case pagination/navigation still
  needs implementation before claiming full case-history browsing.
- Verify actual item report→case→HIDE→audit/outbox→public hidden→RESTORE through
  the new UI and real backend. Current component links do not prove that chain.
- Validate new SQL query plans and bounded media payload on representative
  disposable data. Direct-object privacy for hidden media remains a release gate.
- New audited Admin case creation for unreported content remains a separate
  contract decision/implementation; no SQL shortcut was introduced.

Features stay OFF; no implementation commit/PR/merge, migration, recovered-data
write or deployment occurred. Full V2 objective remains active; G2–G6 are pending.
