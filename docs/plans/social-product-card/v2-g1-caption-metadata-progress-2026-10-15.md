# G1: actual caption presence in Admin summaries

## Approved requirement reconciliation

G1.1 names caption/video presence metadata. Approved ADR-D explicitly delays each
indicator until its real schema exists. V020 now supplies `social_caption`, so
the earlier G1 omission of caption metadata is no longer appropriate. Catalog
media still has the IMAGE-only database constraint; no catalog video schema or
pipeline exists. `hasVideo` remains deferred to G5, not fabricated as false.
This does not authorize enabling catalog video or changing its activation gates.

## Minimal implementation

- `AdminCatalogItemSummaryResponse` adds optional-in-OpenAPI boolean `hasCaption`.
  It means the stored Social caption is non-null, independently of description.
  Existing authoring normalizes cleared/blank captions to null. The metadata
  does not return caption text in list responses or derive presence from a
  commercial description, item status or frontend guess.
- `AdminCatalogReadService` adds one scalar null check to the existing summary
  SELECT and copies it through `withPrimaryMedia`. The same summary is nested
  in detail responses. No extra statement, join, migration, write, permission,
  flag, endpoint or moderation behavior is introduced.
- The current Admin table displays `Có caption` / `Chưa có caption` only when the
  server returns a boolean. An older summary without the optional property gets
  no assertion of presence or absence. Existing small-text styling/components
  are reused; no Figma matching, redesign or new video UI is introduced.
- OpenAPI was exported from the isolated test API; its generated client now
  supplies `hasCaption?: boolean`. No hand-copied frontend DTO was added.

## Targeted evidence

BE RED: two expected failures for missing HTTP boolean/schema property in
`.local/social-v2-g1-caption-metadata-red.log`. UI RED: the new server-confirmed
caption indicator test fails for the missing label in
`.local/social-v2-g1-caption-metadata-ui-red.log`.

BE GREEN: **three tests PASS, 43.705 seconds**, exit 0, in
`.local/social-v2-g1-caption-metadata-green.log`: one actual issued Admin JWT
HTTP test with hidden/deleted items and two focused OpenAPI/export tests. Text
caption produces true; description-only/null caption produces false; list omits
caption text, detail preserves literal multiline text, both omit fabricated
video metadata. Full item-row JSONB snapshots before/after reads are unchanged.

UI GREEN: **eight component tests PASS, 8.12 seconds**, in
`.local/social-v2-g1-caption-metadata-ui-green.log`, including true/false and
older optional-property absence. Transport/navigation are mocked in that suite:
this is not a real Admin browser or four-viewport acceptance claim.

Semantic document comparison, recorded in
`.local/social-v2-g1-caption-metadata-contract-diff.log`, removes only the new
optional property and verifies the whole exported document equals the prior
snapshot: **147 paths and every other schema field unchanged**. API client
generation/check and web typecheck PASS (terminal session 67081, exit 0).
Targeted ESLint PASS (zero warnings/errors) and diff whitespace check PASS.

No unchanged full BE suite, old history/concurrency tests or performance fixtures
were repeated. Previous query-plan measurements predate this scalar projection;
no fresh timing result is claimed here. No recovered/runtime DB access, normal
process restart, remote change, flag activation or deployment occurred. The
normal web/API PIDs remain unchanged; V2/video flags remain OFF.

## Remaining gates

The caption metadata requirement is implemented, not all G1. Catalog video
metadata awaits real G5 capability under ADR-D. Admin browser list/filter/detail/
case navigation/opening, remaining privacy/concurrency/load and staging/release
gates are still open. No staging exists; the whole Social V2 objective remains
active and current project UI remains the owner's visual reference.

Next action: Exercise real Admin list/filter/detail and case flow in the isolated Next/API browser.
Required from you: Nothing
Completion condition: Actual issued Admin cookie requests and authoritative UI responses verify the flow without mocked API/navigation or production data access.
