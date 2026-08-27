# Admin Desktop coverage matrix

Figma source: `06 — Admin Desktop` (`3:7`) in `4LcMXFQz3BnU1EXzBk69t2`.

Base decision: `main` (`42693a64139b8213765dafaa57e4fd66bb3d3736`). The Seller Mobile/Desktop branches do not add Admin route/domain/API blobs compared with `main` and would add unrelated Seller UI commits. No accessible dedicated Admin/moderation/settings branch was found.

Status legend: `FOUNDATION` existing production contract/code to reuse; `TODO` implementation pending; `READ_ONLY` mutation intentionally not exposed; `CONTRACT_GAP` API support must not be fabricated; `RUNTIME_PENDING` needs executable backend/browser verification.

| ADM | Figma node/frame | Route | Existing code | API dependency | Implementation status | Contract gap | Runtime status | Visual status |
|---|---|---|---|---|---|---|---|---|
| ADM-001 | `393:4507` Dashboard | `/admin` | Admin auth layout + API primitives | reports/moderation/settings/audit-backed summary only | TODO | dashboard aggregate/count endpoint not assumed | RUNTIME_PENDING | FIGMA_AUDITED |
| ADM-002 | `393:4746` Users | `/admin/users` | users route + suspension primitive | admin users + suspend/restore, server auth/concurrency | FOUNDATION → TODO | allowed action/version details must come from server | RUNTIME_PENDING | TODO |
| ADM-003 | `393:5003` Seller Provider Accounts | `/admin/merchants` | merchants route + suspension primitive | admin merchants + suspend/restore | FOUNDATION → TODO | allowed action/version details must come from server | RUNTIME_PENDING | TODO |
| ADM-004 | `399:1190` Shops & Locations | `/admin/shops` | shared read-table patterns | authoritative admin shop/location reads | TODO | mutations not assumed | RUNTIME_PENDING | TODO |
| ADM-005 | `399:1413` Listings | `/admin/listings` | moderation primitives | listing/admin moderation reads/actions | TODO | hidden/unknown target fields degrade safely | RUNTIME_PENDING | TODO |
| ADM-006 | `400:1545` Order Explorer | `/admin/orders` | orders route + read-table | admin order search/detail | FOUNDATION → TODO, READ_ONLY | no force-status command inferred | RUNTIME_PENDING | TODO |
| ADM-007 | `400:1818` Order History | `/admin/orders/[orderId]/history` | order domain/history patterns | authoritative order history/snapshots | TODO, READ_ONLY | unavailable snapshot fields stay unavailable | RUNTIME_PENDING | TODO |
| ADM-008 | `404:2216` Need Requests | `/admin/needs` | needs domain exists | admin need reads/moderation if exposed | TODO | moderation mutations only if authoritative API exists | RUNTIME_PENDING | TODO |
| ADM-009 | `404:2473` Reports Queue | `/admin/reports` | moderation route/actions | report cases, allowed actions/version | FOUNDATION → TODO | `allowedActions`/case version is authoritative | RUNTIME_PENDING | TODO |
| ADM-010/011/012 | `407:2674` Policy Settings | `/admin/settings` | admin settings GET/detail/PATCH with `If-Match` | `/api/v1/admin/settings` | FOUNDATION → TODO | ranges/options use server definitions | RUNTIME_PENDING | TODO |
| ADM-013 | `407:2946` Order Timeout Settings | `/admin/settings?section=orders` | settings editor | settings definitions/detail/PATCH | FOUNDATION → TODO | no sample Figma ranges hard-coded | RUNTIME_PENDING | TODO |
| ADM-014 | `407:3149`, `408:3265` Need Matching Settings | `/admin/settings?section=needs` | settings editor | settings definitions/detail/PATCH | FOUNDATION → TODO | server limits authoritative | RUNTIME_PENDING | TODO |
| ADM-015 | `423:17219` Communities & Zones | `/admin/zones` | location/domain read patterns | canonical zone/community reads if present | TODO, READ_ONLY | CONTRACT_GAP until mutation API is proven | RUNTIME_PENDING | TODO |
| ADM-016 | `423:17230` Categories | `/admin/categories` | catalog/category domain read patterns | canonical category reads | TODO, READ_ONLY | CONTRACT_GAP for CRUD unless API proves otherwise | RUNTIME_PENDING | TODO |
| ADM-017 | `417:3838` Audit Log | `/admin/audit` | admin read-table patterns | audit query/filter/pagination endpoint if exposed | TODO, READ_ONLY | sensitive/hidden fields never reconstructed | RUNTIME_PENDING | TODO |

## Required states

All production pages are tracked for loading, empty, error/no-access, unknown or hidden target, long text, pagination, pending/disabled mutation and safe fallback copy. Mutation surfaces additionally track confirmation, no double submit, stale/version conflict and refresh-required/case-changed handling. Orders/history/audit/zones/categories preserve their read-only boundaries unless an authoritative repository API explicitly proves a write contract.

## Desktop contract

Canonical canvas is 1440×900 with 252px sidebar and 1188px main column. Target desktop widths: 1024, 1280 and 1440. At 1024, tables may scroll internally but the page must not introduce horizontal overflow.
