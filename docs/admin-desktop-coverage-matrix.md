# Admin Desktop coverage matrix

Figma source: `06 — Admin Desktop` (`3:7`) in `4LcMXFQz3BnU1EXzBk69t2`.

Implementation branch: `feature/admin-desktop-figma-ui`.

Base decision: branch created from `main` (`42693a64139b8213765dafaa57e4fd66bb3d3736`). Seller Mobile/Desktop branches were intentionally not used because they did not add Admin contracts and would introduce unrelated Seller UI commits.

Status legend: `IMPLEMENTED` production UI wired to an authoritative repository contract; `READ_ONLY` mutation intentionally not exposed; `SAFE_CONTRACT_GAP` route is implemented as an explicit safe state because no Admin read/mutation contract exists; `VERIFIED_LOCAL` executable browser/backend verification passed; `MANUAL_PASS` the current runtime/Figma hashes are approved.

2026-09-19 checkpoint: all 18 Admin cases / 48 captures pass at 1024×900, 1280×900 and 1440×900. The deterministic fixture supplies two open reports, stable audit rows and a recursive location hierarchy. All captures pass automated diagnostics and hash-bound manual review; safe gaps ADM-004/005/008 remain explicit rather than fabricated.

| ADM | Figma node/frame | Route | Authoritative data/contract | Implementation status | Safety boundary | Runtime | Visual |
|---|---|---|---|---|---|---|---|
| ADM-001 | `393:4507` Dashboard | `/admin` | existing Admin users/merchants/orders/moderation endpoints only | IMPLEMENTED | no fabricated `/admin/stats`; unavailable metric degrades safely | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-002 | `393:4746` Users | `/admin/users` | `/api/v1/admin/users`, suspend/restore + Idempotency-Key | IMPLEMENTED | suspend only valid account states; reason + confirmation; server status authoritative | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-003 | `393:5003` Seller Provider Accounts | `/admin/merchants` | `/api/v1/admin/merchants`, suspend/restore | IMPLEMENTED | PAUSED/SUSPENDED/DELETED handled explicitly; moderation status mapped by caller | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-004 | `399:1190` Shops & Locations | `/admin/shops` | no dedicated Admin shop/location read endpoint | SAFE_CONTRACT_GAP | does not reuse Seller/private mutation surfaces | VERIFIED_LOCAL | MANUAL_PASS_SAFE_STATE |
| ADM-005 | `399:1413` Listings | `/admin/listings` | moderation cases exist; no independent Admin listing-read endpoint | SAFE_CONTRACT_GAP | no sample listing data; real reports are handled through moderation cases | VERIFIED_LOCAL | MANUAL_PASS_SAFE_STATE |
| ADM-006 | `400:1545` Order Explorer | `/admin/orders` | Admin order page + detail endpoint | IMPLEMENTED, READ_ONLY | server order-code search/pagination; no force-status command anywhere | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-007 | `400:1818` Order History | `/admin/orders/[orderId]/history` | Admin order detail + Admin events endpoint | IMPLEMENTED, READ_ONLY | every internal event code maps to human copy; unknown codes never exposed; no lifecycle transitions | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-008 | `404:2216` Need Requests | `/admin/needs` | user/merchant Need APIs exist; no Admin Need read/moderation endpoint | SAFE_CONTRACT_GAP | private participant APIs are not repurposed as Admin data | VERIFIED_LOCAL | MANUAL_PASS_SAFE_STATE |
| ADM-009 | `404:2473` Reports Queue | `/admin/reports` | Admin moderation page/detail/action endpoints | IMPLEMENTED | case status/action server-owned; destructive actions confirm; 409/412 shows reload-required state | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-010/011/012 | `407:2674` Policy Settings | `/admin/settings` | `/api/v1/admin/settings`, detail/PATCH + If-Match | IMPLEMENTED | exact registry keys, server definitions/scopes/value types; global changes confirm | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-013 | `407:2946` Order Timeout Settings | `/admin/settings?section=orders` | setting registry + settings API | IMPLEMENTED | no Figma sample ranges hard-coded; server validation authoritative | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-014 | `407:3149`, `408:3265` Need Matching Settings | `/admin/settings?section=needs` | setting registry + settings API | IMPLEMENTED | matching limit sourced from registry/API | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-015 | `423:17219` Communities & Zones | `/admin/zones` | canonical `GET /api/v1/location-areas` | IMPLEMENTED, READ_ONLY | no zone mutation API exists, so no create/edit/delete controls | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-016 | `423:17230` Categories | `/admin/categories` | canonical `GET /api/v1/categories` | IMPLEMENTED, READ_ONLY | no Admin category CRUD contract exists | VERIFIED_LOCAL | MANUAL_PASS |
| ADM-017 | `417:3838` Audit Log | `/admin/audit` | `/api/v1/admin/audit-logs` filters + Spring pagination | IMPLEMENTED, READ_ONLY | metadata is summarized with sensitive-key suppression; no raw private blob dump | VERIFIED_LOCAL | MANUAL_PASS |

## Implemented Admin Desktop contract

- Canonical Admin sidebar follows the frozen Figma IA: Dashboard, Users, Seller / Provider, Shops & Locations, Listings, Orders, Cần mua, Reports, Communities / Zones, Categories, Settings, Audit Log.
- Desktop canvas targets the Figma 1440×900 composition: 252px sidebar + flexible main column. At narrower desktop widths, tables scroll internally rather than introducing page-level horizontal overflow.
- Status chips are presentation-only: pages map known domain states and fall back to `Trạng thái đã được cập nhật` rather than parsing unknown enums inside the chip.
- Orders are strictly read-only in Admin. The UI never renders or calls a force-order-status command.
- User/merchant suspension uses confirmation, Idempotency-Key, reason requirements, disabled invalid states, no double-submit, and refresh after success.
- Moderation/report actions use explicit confirmation; destructive actions require a reason code; 409/412 is treated as case-changed/stale state and requires reload.
- Settings use server-provided definitions/details and If-Match when a persisted GLOBAL version is available. Registry keys are grouped explicitly instead of guessing sections from substrings.
- Audit filtering is sent to the server (`actorUserId`, `action`, `entityType`, `entityId`, `requestId`, `createdFrom`, `createdTo`, `sort`) with page size 50. Audit metadata is shown only as a short safe summary.

## Verification result

No fake API, mock production dataset, or Seller-private Admin substitute was added. Web typecheck, zero-warning lint, unit tests and production build pass; authenticated Admin Playwright and the 48-capture manual ledger pass locally. Remote CI/merge and staging/release verification remain separate and incomplete.
