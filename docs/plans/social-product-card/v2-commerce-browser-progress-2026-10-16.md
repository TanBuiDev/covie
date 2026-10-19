# Social V2 — disposable cart/checkout/pickup/order/verified-review chain

Date:2026-10-16. Scoped local11.3.9 evidence. Not staging or full release acceptance. No production application correction.

## Fixture audit and safety

Read-only inspection established `scripts/seed-ui-fixture.ps1` is not a safe reusable seed for existing recovered data: it defaults to normal compose PostgreSQL, deletes transient order/review/fulfillment/inventory data for its fixed identities, changes identity hashes and system settings, and writes the runtime fixture manifest. It was **not executed** and the existing credential manifest was not opened. Existing local browser suites similarly must not run against that data as a substitute for disposable verification.

Added opt-in `SocialCommerceBrowserIntegrationTest` using existing disposable PostgresIntegrationTest pattern and `apps/web/e2e/social-commerce-browser.mjs`. Fixture creates one random Buyer and OWNER Seller, one ACTIVE/APPROVED merchant, pickup-enabled location, PRODUCT/STANDARD item with distinct commerce description/Social caption, default variant and25000VND UNTRACKED offer. Search index built with existing service before snapshots. Review policy stays at existing true default; no system setting mutation or fabricated completed-order/review seed. Actual initial sessions are issued and passed through HttpOnly cookies, not login-form proof. No images/storage writes are needed for this caption/commerce fixture; this is not new MinIO/CDN evidence.

Only isolated API previews and Next Social preview flag are ON. Normal V2/video flags OFF; no recovered/runtime DB or normal service use. Outbox processing and retention disabled for fixture; dispatch/reputation consumer execution is not claimed.

## Actual accepted browser chain

Chrome155.0.8059.84/1280, actual Next/API/PostgreSQL with unmodified bridged responses, separate Buyer/Seller contexts, seeded coordinate preference (not GPS):

1. Buyer Search Social sees actual stored caption; native “Xem & Đặt” opens exact item. Native “Thêm vào giỏ” produces actual `/api/v1/me/carts/items`201.
2. Buyer opens real cart list/detail and “Tiếp tục đặt hàng”. Current UI auto-previews PICKUP/COD, actual preview200 (two dev requests observed), then native “Đặt hàng” commit201 exactly once with Idempotency-Key and success route/heading.
3. Seller opens actual same-order detail and performs native confirm200, ready-for-pickup200 with actual confirmation dialogs.
4. Buyer opens order and native confirms receipt200, then opens review page, chooses5stars and submits actual review201. Native submitted-review screen rendered and contextual screenshot inspected.
5. Actual public item-review HTTP200 returns the real review with verifiedPurchase=true, reviewCount1/averageRating5. This is public API proof; not a separate public-page badge rendering assertion.

Exactly8 API writes: cart201 +2preview200 +commit201 +Seller2commands200 +Buyerreceipt200 +review201. All normal CSRF; commit/order commands/review carry idempotency keys. No duplicate commit/order/review. Java independently checks one COMPLETED order linked to actual Buyer/merchant/location, total25000, PICKUP/COD via actual product fulfillment join; one order item for exact catalog item, one product fulfillment/no service fulfillment, one5star review linked to the exact order item. All11 protected Catalog/merchant/location/category/media/reaction/comment full-JSONB tables unchanged, including Social caption and commerce offer. Checkout/cart/order/review/rating/idempotency/outbox effects are intended and are not incorrectly declared readonly.

## Harness corrections and verification

Earlier failures were test assumptions, not product RED:
- `.local/social-v2-commerce-browser.log`: cart succeeded201 at current `/api/v1/me/carts/items`; helper waited for an obsolete `/api/v1/cart...` prefix. Corrected helper only.
- `...-verified.log`: current UI already auto-previewed200; helper timed out waiting for obsolete manual “Xem trước” step. Source confirms autoPreview effect. Final helper waits for actual preview200 then uses native commit. Order assertion also uses product_fulfillments.fulfillment_method rather than an assumed orders column.

Final **1PASS84.64s**, Maven**01:27min**,0skip: `.local/social-v2-commerce-browser-final.log`, same-basename evidence directory; pageErrors=[] after context close. Targeted helper and `staging-p0.spec.ts` ESLint, node syntax and focused whitespace checks PASS. Eight relevant app hashes matched validation copies (Cart list/detail, CheckoutPreview, OrderActions, ReviewForm, CheckoutCommitService, ReviewService, RatingProjectionService); Java fixture match. Manifest deleted, owned API50935/Next50937 stopped, normal3000/8081 absent/untouched.

The existing named staging-P0 helper had the same confirmed obsolete manual preview click. Removed that one line; its existing wait for actual commit button remains. This is test-only compatibility maintenance backed by the actual UI failure and successful current flow. The staging suite itself was **not run**; no staging exists, so no staging PASS or credentials invented. Older populated-local suite is not invoked and remains a separate artifact to reconcile before any future safe use.

No production BE/FE/API/schema/client/CSS/migration change, full suites, old successful Social/browser/concurrency flows, typecheck/build rerun, normal restart, secret-manifest access, remote/commit/branch/push, activation or deployment. Whole objective ACTIVE.

```text
mvnw.cmd -o -Dtest=SocialCommerceBrowserIntegrationTest -Dcovie.test.social-commerce-browser=true -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-commerce-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-commerce-browser-final test
```

This closes a concrete local PRODUCT/PICKUP/COD native11.3.9 chain from Social discovery through verified review. It is not all shipping/service/stock/variant/account/state combinations, login-form, mobile/four-width/a11y, outbox dispatch, staging/load/recovery or exact release proof. Preserve working commerce code and do not rerun this unchanged successful flow.

Next action: Reproduce the concrete desktop Search price-input accessible-name gap from11.4 before a minimal label fix.
Required from you: Nothing
Completion condition: Focused accessibility-name evidence determines the smallest correction, preserving price/filter/API behavior and avoiding broad regression reruns.
