# Social V2 — đối chiếu gate G1–G4

Full-plan requirement/gate audit: `v2-g0-g6-requirement-audit-2026-10-16.md` reconciles G0–G6/8–18 and supersedes interpreting every limitation below as an independently mandatory invented gate. It preserves actual missing video/perf/staging/recovery requirements; scoped local evidence is not full release acceptance.

Ngày: 2026-10-16. HEAD: `bb2e06b4`; implementation nằm trong worktree dirty, không phải toàn bộ đã commit/deploy. Đây là đối chiếu nguồn và bằng chứng đã lưu, **không phải nghiệm thu toàn bộ**. Không chạy lại Maven/browser/typecheck chỉ để tổng hợp báo cáo này.

## Cách đọc và giới hạn bằng chứng

- **Local evidence**: có test/log/artifact cho phạm vi ghi rõ; không thay thế staging, load hoặc mọi trạng thái/browser.
- **Source present**: thấy implementation hiện tại; chưa đủ để khẳng định mọi hành vi đã đạt.
- **Open verification**: chưa có bằng chứng đúng phạm vi. Không đồng nghĩa chắc chắn có bug và không tự tạo yêu cầu nghiệp vụ mới.
- **Candidate gap**: khác biệt cụ thể cần tái hiện/đối chiếu contract trước khi sửa.
- Báo cáo cũ là lịch sử. Chỉ mang tiếp kiểm chứng chưa bị thay đổi liên quan; các fix sau có regression mục tiêu riêng. Không cộng các con số PASS thành một bộ test đồng thời cho toàn bộ worktree hiện tại.
- Owner đã bỏ yêu cầu khớp Figma; không còn gate chờ frame. Owner đã duyệt yêu cầu/ADR riêng; không hỏi lại. Approval không cung cấp staging hoặc chứng cứ vận hành.

## G1 — Admin

| Yêu cầu kế hoạch | Nguồn/bằng chứng hiện có | Phần còn mở / cách xử lý |
| --- | --- | --- |
| G1.1 Admin-only, hidden/deleted readonly, private/no-store, không PII/storageKey | `AdminCatalogReadService`, controller/DTO, `AdminCatalogReadIntegrationTest`; `v2-g1-backend-progress-2026-10-15.md` | Giữ implementation. Không suy ra object/CDN privacy từ JSON không có storageKey. |
| Paging/sort allowlist, name/ID/merchant search, combined filters | Service hiện có escaped q, item/merchant name+ID, listingKind/type/status/merchant filters; HTTP/browser fixtures | Local search/filter/paging đã có. Không thêm date/report aggregate nếu contract chưa có. |
| Bounded queries, indexes, case history >50 | V021/V022, opt-in actual query plans + populated migration checks; `v2-g1-case-index-progress-2026-10-15.md`, `v2-g1-read-query-plans-progress-2026-10-15.md` | Synthetic/local query plan không phải staging P95, index build/disk/long-transaction gate. Không thêm index tiếp khi chưa có evidence. |
| Caption/video indicators | Có `hasCaption` từ cột thật; `v2-g1-caption-metadata-progress-2026-10-15.md` | `hasVideo` phụ thuộc G5 schema thật theo ADR-D; không tạo boolean giả để đóng G1. |
| Case thật, open SYSTEM_FLAG, reason/confirmation/idempotency/audit/outbox | Backend/UI/history, real report–HIDE–RESTORE chain, concurrent Admin openings; các báo cáo G1 tương ứng | Local pipeline đã có. Không nhân bản `/admin/reports` hoặc ghi thẳng item status. |
| Admin UI states, detail/history/action conflicts | `admin-catalog-screen.tsx`, history401/403/pending200 RED→fix; actual logout204→history401 clears screen/retry denied, seven-table readonly; thêm actual stale APPROVED→Node open/HIDE→browser opening409→authoritative HIDDEN/RESOLVED refresh, exact effects; `v2-g1-access-denial-progress-2026-10-16.md`, `v2-g1-opening-conflict-browser-progress-2026-10-16.md` | Actual role-removal403/history continuation và mọi error ordering chưa được chứng minh toàn bộ.401 browser và403 component là hai mức evidence. Không tự tạo mandatory gate cho mọi ordering ngoài kế hoạch; không lặp happy path. |
| Responsive/keyboard/security/staging | Real four-width Admin capture và CSS corrections; BE guest/non-Admin denial | Không phải full keyboard/screen-reader acceptance; staging browser chưa có. |

## G2 — Seller authoring

| Yêu cầu kế hoạch | Nguồn/bằng chứng hiện có | Phần còn mở / cách xử lý |
| --- | --- | --- |
| G2.1 nullable additive caption, description tách biệt, Unicode/newline/HTML literal, PATCH presence, ETag | V020/entity/DTO, actual JWT/PG Create/PATCH/OpenAPI tests; thêm actual published Seller two-tab PATCH204/v1→stale409→Admin PENDING/new literal caption/case1 với exact DB effects, `v2-published-caption-edit-progress-2026-10-16.md` | Local populated upgrade và preservation đã có; staging-scale lock/time và rollback drill vẫn mở. Không sửa migration đã apply; không lặp unchanged local chain. |
| PRODUCT/SERVICE/RESALE Create/publish và commerce preservation | Existing3type BE guards plus actual native all3type upload/Create201/DRAFT/Publish204/ETag→fresh guest feed, exact DB/SDK byte checks in `v2-seller-publish-feed-progress-2026-10-16.md` | Scoped local AUTO_PUBLISH/1280 proof, not all policies/states/roles/staging. No repeat of unchanged success; inspect published Edit coverage separately. |
| Moderation after edit, PAUSED không tự ACTIVE, HIDDEN/REJECTED restore không bypass | `CatalogEditService`, catalog handler; PAUSED/restore reports; two HIDE + four RESTORE contention schedules | Local sequential/race evidence đã có; không chạy lại khi related source không đổi. Không suy rộng sang mọi policy/type hoặc cache/in-flight delivery. |
| Quyền/ownership, revoke và merchant-state transaction | Shared membership guard scoped V2 Create/PATCH; issued JWT revocation reports. Separate actual Admin suspension-first/Create lock contention200/422/no Create effects trong `v2-create-merchant-suspension-progress-2026-10-16.md` | Preserve correct Create lock/recheck. Edit UPDATE hiện không yêu cầu ACTIVE merchant theo action policy, không tự thêm prohibition. Không biến opposite ordering/downgrade/lifecycle thành bug mặc định hoặc suy ra all-orders proof. |
| READY/purpose/ownership, tối đa10, ordering/cover/legacy preservation | Existing attach policy + V2 limits, targeted real PG + Seller MinIO/browser Create/Edit | Real upload success/bytes/order có; browser failed upload/retry recovery chưa có cùng độ mạnh với component tests. Không thay legacy galleries. |
| Shared draft preview, clear/undo, dirty navigation/refresh, hydration | Current Seller forms/gallery/preview; link/Back/Forward/reload/independent offer refresh/hydration reports | Local PRODUCT native-browser paths đã có, không coi Firefox/Safari/native OS picker hoặc mọi independent command đều đã chứng minh. |
| Moderation status và lý do theo quyền; entry quản lý phản hồi | Scoped reasonCode JWT/schema/client/UI và actual Seller browser; thêm exact executed reason SQL tại20kitems/200050cases+actions, lookup1/1/0 với existing indexes trong `v2-g2-seller-reason-query-plan-progress-2026-10-16.md` | Warm synthetic plan không thay load/staging SLO; other-role/browser và full a11y gates còn mở. Không dùng Admin notes/reporters hoặc giả dashboard/deep-link/DRAFT public route. |
| G2.4 bốn widths/keyboard/error/refresh | Four-width captures trong real storage/navigation proofs, component tests label/reorder/errors | Cần phân biệt screenshot contextual với toàn bộ error/keyboard acceptance. Không redesign form commerce. |

## G3 — Social feed

| Yêu cầu kế hoạch | Nguồn/bằng chứng hiện có | Phần còn mở / cách xử lý |
| --- | --- | --- |
| Batch1–40 strict/dedup, cap6 READY, visibility, no viewer flags/storageKey, no-store, no N+1 | `CatalogSocialPreviewIntegrationTest`, actual SQL plan fixture, generated adapter; preview BE/query/feed reports | Giữ contract map keyed item ID; không đổi thành array hoặc fetch detail từng card. Local plans không thay staging/load/cold-cache proof. |
| Social-only; Catalog legacy path; Home/Search reuse | Production consumers Home + Search; prior buyer Search plus actual no-cookie/session Guest Home/Search route/toggle/no mutation/preview/image proof in `v2-guest-home-search-browser-progress-2026-10-16.md` | Guest local1280 proof không phải full responsive/staging acceptance. Không mặc định merchant page là Social consumer hoặc tự thêm Social mode ở đó; không lặp unchanged functional route proof. |
| Literal/clamped caption/gallery, fallback, next/prev, keyboard/swipe/no accidental link | Shared SocialPostContent + card tests; real feed/gallery-touch reports | Real Chrome/CDP trusted touch, không phải physical phone/cross-browser. Không yêu cầu pixel Figma nữa. |
| 360/768/1280/1440 layout, vertical scroll, toolbar, touch targets | Existing CSS social1col/desktop2col, reduced motion/focus; browser captures and toolbar correction | Full long-name/address/price/no-media/offline/a11y acceptance chưa chứng minh hết. max359px42px rule không phải bằng chứng vi phạm tại360; đo thật trước khi sửa. |
| Like/Save server-confirmed, duplicate cards, tab reconciliation | Đã tái hiện/sửa pending Like vs focus GET stale overwrite; duplicate-provider/omission/fresh-read component controls và actual API browser old0→confirmed1→old response→UI1 trong `v2-g3-summary-overlap-progress-2026-10-16.md` | Actual browser một provider/synthetic focus; native/cross-tab và mọi Save/comment ordering vẫn riêng. Actual login flow đã tạo fresh document; không thêm polling bằng suy đoán. |
| Item HIDE khi feed đang mở | BE visibility/races, component duplicate-provider regression/guards; actual Next/report201/Admin HIDE+replay200/Like404/fresh omission chain đạt trong `v2-g3-unavailable-browser-progress-2026-10-16.md` | One-provider Chrome local, không phải eventless HIDE, duplicate-provider browser, cross-tab/account hay object/CDN revocation proof. Không đặt TTL/realtime bằng suy đoán. |
| Lazy comment/thread/retry/report/429 và share permission | Existing social adapter/comment/card implementation and focused interaction tests; share catch không báo thành công khi permission rejected | Browser proof phần mới chưa bao quát toàn bộ interaction/error states. Comment DELETE/HIDE và bulk conflict đã kiểm chứng/sửa riêng; không viết lại handler đúng. |
| Ads/search cursor/preference/no fabricated metrics | Existing feed/discovery wiring giữ nguyên, no new analytics field | Không thêm metrics giả hoặc refactor pagination/Ads không có evidence regression. |

## G4 — Hoạt động của tôi

| Yêu cầu kế hoạch | Nguồn/bằng chứng hiện có | Phần còn mở / cách xử lý |
| --- | --- | --- |
| Actor-only required type/current-state, private/no-store, keyset/max50, ACTIVE comments on visible items | `SocialActivityReadService`, actual JWT tests + full restricted visibility matrix/comment cursor proofs | Local BE semantics đã chứng minh; không thêm event log/audit-history hoặc userId selector. |
| Bounded cover + indexes/query plan | Actual issued HTTP/query plan report with9observations, existing V018 indexes | Synthetic warm fixture không phải heavy-account/load/staging P95; không thêm index khi hiện tại đã đạt bounded plans. |
| `/me` entry, URL tabs, literal excerpt, comment identity, append/retry/auth states | Route/profile/screen/hook current source and focused UI/client tests | Entry/UI đã tồn tại, không quay lại trạng thái “chưa làm UI/chờ Figma”. MVP không yêu cầu inline Unlike/Unsave hoặc invented comment deep-link. |
| Real Next/API/cookies, mutation→activity/tab/account reconciliation | Prior pending-page/focus + real email login/logout/Back401/loginB proof; added actual Buyer native Like/Save/comment201→reload→three private tabs with exact UUID/DB effects in `v2-buyer-activity-chain-progress-2026-10-16.md` | No dedicated in-page switch. One local1280 buyer chain does not prove staging/native cross-tab/OAuth/BFCache/all mutation-removal cases; retain scoped prior evidence without unchanged reruns. |
| Four-width/current shell, error image/unavailable, a11y | Actual Next captures + scoped CSS fix, screen/hook tests | Full keyboard/screen-reader/cross-browser/load/staging acceptance còn mở. |

## Bằng chứng đã kiểm tra trong lần đối chiếu này

Đã đọc kế hoạch G1–G4/8.3/12.4, ADR, service Admin/Create/Edit/activity, Social provider/card/error adapter, Seller form/DTO, current consumer map và các báo cáo liên quan. Đã kiểm tra file log thực sự tồn tại và có terminal success, không chạy lại:

- `social-v2-g1-admin-contract.log` (5), `social-v2-g1-read-plans-green.log` (3), `social-v2-g1-admin-browser-final.log` (1).
- `social-v2-g2-live-browser.log` (1), `social-v2-g3-feed-browser-accepted.log` (1), `social-v2-g3-gallery-touch.log` (1), `social-v2-g3-toolbar-green.log` (1).
- `social-v2-g4-live-browser.log` (1), `social-v2-g4-query-plans.log` (1).
- `social-v2-comment-conflict-green.log` (3), `social-v2-create-revocation-green.log` (3).

Số trong ngoặc chỉ là số case của từng historical run, không phải aggregate current full-suite evidence. Các artifacts ở `.local/` không phải deliverable staging bất biến và không được publish token manifests.

## Thứ tự xử lý còn lại

Current Search-label/CTA-corrected configured production web build/runtime smoke now locally verified with593source/context hashes plus independent path-set match; `v2-ui-final-production-build-progress-2026-10-16.md`. This supersedes the fresh-build gap mentioned below. Local non-root/image/assets only, not staged/signed release or whole11.4/G6 acceptance; no unchanged rebuild.

Scoped11.4 actual40control/reduced-motion observations at four widths now verified. Only primary CTA38px failed; a single shared.cta min-height44 declaration fixes it, `v2-social-controls-accessibility-progress-2026-10-16.md`. No business/React/API change. Not every inline link/whole-page a11y/staging; preserve achieved measurements and final stable-build delta.

Desktop Search price-label candidate now confirmed RED (two empty computed names) and fixed by exactly two aria-label attributes; scoped tests/typecheck/lint PASS, `v2-search-price-label-progress-2026-10-16.md`. Prior production image predates these attributes: retain old packaging smoke, perform final exact-source build after UI settles. No full11.4/staging claim or unchanged native price-flow rerun.

Local11.3.9 PRODUCT/PICKUP/COD native Social→cart/checkout→Seller fulfillment→Buyer completion→review/public verifiedPurchase chain now verified with exact order/item/location/review and11protected table preservation; `v2-commerce-browser-progress-2026-10-16.md`. No commerce app correction. Staging-P0 single obsolete manual preview click removed, suite not executed. Preserve achieved local flow; staging/full release/11.4 remain separate.

Local11.3.8 Search native ASC/DESC/max-price/reload/Catalog/clear chain now verifies actual differing result order/UUIDs, URL parameters and11table readonly effects; `v2-search-filters-browser-progress-2026-10-16.md`. Keep this and prior populated Ads evidence; no unchanged functional rerun. Desktop min/max input source lacks explicit labels, a concrete11.4 review candidate rather than a proven price-function bug.

Scoped11.3.8 populated Home HOME_HERO/IN_FEED actual browser modes/disclosures/native click→indexed exact Search deep link and intended delivery/telemetry persistence now locally verified; `v2-home-social-ads-browser-progress-2026-10-16.md`. Campaigns and protected Catalog/social rows unchanged. Do not rerun unchanged success or treat fixed sort URL as native filter/sort-control/full commerce/staging proof.

Local11.3.7 initial public eligibility now verified using actual guest Search and direct browser preview/summary/comment reads against three persisted merchantHIDDEN/SUSPENDED/deleted-item aggregates with stored comments; all14 business tables unchanged. See `v2-public-ineligible-browser-progress-2026-10-16.md`. No app correction; lifecycle/idle-tab/CDN/staging boundaries remain explicit, no unchanged success rerun.

Local11.3.6 native Admin search/detail/open→HIDE→RESTORE now verified with independent guest reload/actual preview/comment probes and exact DB/audit/outbox effects in `v2-admin-hide-restore-browser-progress-2026-10-16.md`. Existing code correct, no app fix. This does not substitute for staging, idle-tab realtime or private object/CDN proof; do not repeat unchanged local chain.

1. **G3 confirmed-unavailable:** component RED/fix/guards và actual isolated browser report/HIDE→Like404→preview omission đã đạt theo phạm vi báo cáo; không lặp lại khi source liên quan không đổi. Object/CDN/eventless/cross-account gates vẫn tách riêng.
2. **G2 missing workflow:** entry/reason và scoped actual SQL plan đã có local evidence; giữ nguyên BE/index đang đạt. Load/staging vẫn riêng; không lặp browser/query đã đạt khi related source không đổi.
3. **Account/duplicate consistency:** actual email auth/Back và scoped G3 overlap fix/proof đã có. Không lặp khi source không đổi; native cross-tab/other auth and completion orders vẫn riêng, không tự thêm polling/cache.
4. **Remaining UI/error/build gates:** current post-G1-fix Next16.4/Turbopack Docker build + non-root runtime/public-assets smoke đã đạt local với582file hash/path-set match theo `v2-current-production-build-progress-2026-10-16.md`; previous public COPY/context fixes retained, verifier now rejects forbidden directories/inspection failures. Không coi local image là release/staging; không rerun unchanged build. Tiếp tục dùng route/fixtures hiện có cho literal missing browser requirements; không biến mọi caveat thành gate giả định hoặc chạy broad suites liên tục.
5. **Infra-bound gates:** private storage/CDN object access/hide behavior, staging migration/index costs, real performance/load, Phase4 staging runner, backup+restore+app rollback, immutable images và release evidence. Chưa có staging; phải giữ UNVERIFIED, không giả lập thành production proof.

G5 chưa có CATALOG_VIDEO/probe/poster/mixed gallery implementation; chỉ baseline/storage checks. Kế hoạch yêu cầu G1–G3 và cost/content-safety/storage prerequisites trước khi bắt đầu video. Owner approval không làm những prerequisite kỹ thuật tự PASS. G6/release chưa đạt. Toàn bộ mục tiêu vẫn ACTIVE; tất cả normal V2/video flags OFF. Không runtime/recovered DB access, restart, schema/API/client/business change, remote Git operation, activation hoặc deploy trong lần đối chiếu này.
