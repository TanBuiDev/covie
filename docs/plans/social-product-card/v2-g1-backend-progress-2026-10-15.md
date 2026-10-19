# Social Product Card 2.0 — G1 backend progress

Base/working HEAD: `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`, main with
uncommitted work; no implementation commit/PR/merge/deployment performed.

## Implemented locally

- Added `GET /api/v1/admin/catalog-items` and `/{itemId}` using AdminAccessPolicy,
  private/no-store responses and read-only transactions.
- Default-OFF property: `covie.social-v2.admin-reads-enabled`. Off returns 404 to an
  authorized Admin; authentication and role checks still precede reading data.
- List supports page/size, q (item/merchant name or ID), type, listingKind,
  listing/moderation status, merchant ID and allowlisted deterministic sort.
  Existing page max 100 retained; overflowing offset and invalid query are 422.
- Detail includes description, ordered associated media and provider locations,
  recent 50 item cases plus total count. This is a bounded recent-case projection,
  not a claim that all case history fits in one response; further case browsing
  remains part of G1 UI/contract work.
- Media URLs resolve only READY records; pending media exposes status without a
  URL. No raw storageKey, upload URL or buyer/seller PII property is added.
- Existing public filtering and moderation write paths remain unchanged. No
  direct Admin item-status mutations or new migration were added.
- Caption/video fields are not fabricated while their schema is absent.
- Generated OpenAPI and TypeScript client contain the two new routes. Semantic
  comparison proves all pre-existing route/schema definitions unchanged.

## Verification

The API launcher was already running from apps/api/target. Maven verification used
an isolated source copy in `.local/social-v2-api-validation/api`, with its own
target directory and disposable Testcontainers PostgreSQL. No recovered database
or running application was restarted or migrated.

| Check | Evidence |
| --- | --- |
| HTTP missing-feature RED | `.local/social-v2-g1-admin-red.log`: 2 failures, Admin expected 200 received 404 and non-Admin expected 403 received 404 |
| Initial API/flag GREEN | `.local/social-v2-g1-admin-green.log`: 3 tests pass, 35.964 s |
| Media/read-only/auth/paging/flag + export | `.local/social-v2-g1-admin-contract.log`: 5 tests pass, 0 failures/errors/skips, 35.171 s |
| Generated client | `pnpm api-client:generate` + `pnpm api-client:check`: exit 0 |
| Existing contract compatibility | Recursive semantic comparison with `.local/social-openapi-20261015.json`: old routes/schemas unchanged; only two Admin routes added |
| Existing FE type compatibility | `.local/social-v2-g1-web-typecheck.log`: exit 0 |

The HTTP tests use actual issuer JWTs, Spring security and PostgreSQL. They prove
guest 401, non-Admin 403, Admin list/detail, hidden/deleted records, filtering/paging,
safe sort/offset limits, READY/PENDING media behavior, item/media row immutability
and default-OFF feature behavior. They do not prove all G1 state combinations,
moderation mutation chains, query plans or real browser/staging behavior.

## UI design evidence and remaining work

Retrieved Admin page metadata and exact production frame
[ADM-005 Listings 399:1413](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=399-1413)
with high-fidelity get_design_context and screenshot, following figma-design-to-code.
No UI file was edited by this backend change.

Next: build the gated real Admin listing/filter/detail UI from generated types and
shared Admin components; link existing moderation cases, refresh authoritative
state, cover loading/error/empty/unauthorized and responsive behavior. Add targeted
case-link and read projection checks where actual UI contracts require them.
Measure the new SQL on disposable representative data before calling its performance
gate achieved. Do not create arbitrary case SQL writes.

F08 is partially implemented; F09 case actions already exist but their new UI
integration is pending. F01–F07 and G2–G6 remain active, not complete. No staging
exists; Phase 4/V2 staging/load/recovery/release gates stay UNVERIFIED. READY media
URL projection is not proof of direct-object privacy for hidden content; storage
delivery/privacy verification remains required before feature activation.
