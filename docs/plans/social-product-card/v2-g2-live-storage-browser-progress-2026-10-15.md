# Social Product Card 2.0 — G2 real browser/storage checkpoint

## Scope and minimal production changes

The existing Seller caption/gallery implementation was kept unchanged: the
targeted real-system chain passes without an application-code repair. Changes
to runnable production configuration are limited to `infra/storage/cors.json`:

1. Allow the existing presign's required `If-None-Match` header. Origins, methods,
   exposed headers and cache age are unchanged; conditional overwrite protection
   is not removed. AWS requires preflight-requested headers to match a rule's
   allowed headers. See [AWS CORS rules](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ManageCorsUsing.html).
2. Wrap the rules in `CORSRules`. The apply script passes the file directly to
   `aws s3api put-bucket-cors --cors-configuration file://...`, whose parameter is
   a structure containing CORSRules, not the console's bare array. See the
   [AWS CLI parameter shape](https://docs.aws.amazon.com/cli/latest/reference/s3api/put-bucket-cors.html).

Updated `docs/operations/object-storage.md` to distinguish these file formats and
preserve signed conditional writes. No AWS/bucket deployment was performed.

## Configuration regression evidence

Added one opt-in `StorageUploadCorsContractTest`, using the actual S3ObjectStorage
presign code and SDK presigner with dummy static credentials. It does not call
AWS or a storage endpoint. It compares required header names with the actual
deployment file, and checks the AWS CLI wrapper consumed by the apply script.

- `.local/social-v2-g2-storage-cors-red.log`: fails specifically because
  `if-none-match` is not allowed by the original configuration.
- The header-only correction passes in `-storage-cors-green.log` (2.610 s).
- Strengthened shape check fails on the original bare array:
  `.local/social-v2-g2-storage-cors-shape-red.log`.
- Final `.local/social-v2-g2-storage-cors-final.log`: **1 pass, 2.721 s** for both
  required headers and CLI structure. This is not a live AWS CLI/apply rehearsal.

## Real Seller chain

Added one opt-in `CatalogSocialBrowserIntegrationTest` and durable
`apps/web/e2e/catalog-social-browser.mjs`. It runs actual Next 16.4 dev/webpack,
random-port API, disposable PostGIS/PostgreSQL and MinIO. The browser remains at
localhost:3000 using context-scoped forwarding to the isolated Next instance;
actual HMR traffic is forwarded too. Normal application processes are untouched.

The OWNER and merchant/category/location are disposable fixtures. Playwright
installs a real InitialSessionIssuer token as an HttpOnly cookie. The login form
is not part of this case. Browser writes use the current generated API adapter
and its normal CSRF path; API responses are not fabricated.

The case verifies:

1. Select real category/location, enter name, commercial description, price 25000
   and a separate HTML-looking/newline caption. The UTF-16 counter reflects the
   raw value; the shared preview trims edges, preserves newline and renders the
   tag text literally, with no `<b>` element.
2. Choose two real PNG files through browser File objects. Native drag/drop
   (captured isTrusted=true) makes second.png the cover before Create.
3. Browser performs direct presigned cross-origin PUT, then actual API completion
   and Create 201. Opening the real Edit route reads back the saved trimmed
   caption and ordered two-media model.
4. Add third.png alongside existing media; keyboard Enter on its cover control
   moves it first. Real PATCH 204 and reload preserve the edited caption and
   order third/second/first, sort 0/1/2 and the actual primary flag.
5. Variant and location-offer DTOs are unchanged across the edit; description and
   listing state are unchanged. A second actual PATCH clears only the caption;
   after reload its field is empty and media/offers/description remain unchanged.
6. The three saved images load from MinIO and decode at their true 8px width.
   There are exactly three successful browser PUT responses and real GET 200s.
   Backend assertions independently confirm one item, caption NULL, DRAFT,
   description unchanged and price 25000. Each stored object is read through the
   SDK and compared byte-for-byte with the corresponding third/second/first PNG.

Final `.local/social-v2-g2-live-browser.log`: **1 pass**, test elapsed **63.54 s**,
Maven total **01:06 min**, zero failures/errors/skips. JSON, eight screenshots,
test PNGs and redacted browser/Next logs are under
`.local/social-v2-g2-live-browser/`. All four measured document widths equal their
viewports: 360/768/1280/1440. Screenshots assert the route and exact relevant
landmark before capture; they were inspected. The 360px caption capture's framing
cuts off the upper control; it is not complete visual acceptance of that field.
Preview captures show the literal content and gallery at all four widths.

## Harness facts and limitations

- A preliminary browser attempt used getByLabel on a controlled textarea whose
  label textContent includes its current text. A small actual-browser probe
  confirms the accessible textbox name remains Caption. The helper now selects
  that actual role/name; no working caption component was altered to satisfy it.
- Chrome initially blocked the loopback storage request under Local Network
  Access, before storage PUT. The disposable context now grants that permission
  only for localhost:3000; web security/CORS are not globally disabled. This is a
  fixture setup requirement, not proof of production-network behavior.
- MinIO's test-only CORS origin is localhost:3000. Its disposable bucket allows
  anonymous GET of its fixture objects so the existing public URL model can be
  rendered. **This is not the production private-origin/CDN policy** and does not
  prove object revocation, hidden-item privacy or live AWS CORS configuration.
- The run uses the locally cached MinIO image override
  `cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1`;
  no production image or dependency was changed. No storage response mocking or
  browser upload proxy was used.
- Browser File objects are exercised via setInputFiles, not a native OS picker.
  Existing Next image-sizing warnings are recorded; there are no uncaught page
  errors. This is not a claim that every warning or image/codec scenario is closed.

Targeted ESLint for the new browser helper passes in
`.local/social-v2-g2-live-browser-lint.log`; diff whitespace checks pass. Tested
Seller form/gallery/caption/preview/uploader copy hashes match the worktree.
No unchanged TypeScript build, generated client or broad BE suites were rerun.
The successful Seller chain was not rerun after the later CORS-wrapper-only
correction, which is covered by its focused source contract test instead.

Owned child processes stop in finally and the token manifest is deleted. Normal
ports 3000/8081 retain their original processes. No recovered DB was queried or
written, no runtime migration/restart/normal feature activation/commit/deployment
occurred. Only isolated fixture flags are ON; normal V2/video flags remain OFF.

## Remaining gates

This proves a PRODUCT draft browser chain, not all G2/G1–G6 completion. Published
moderation/concurrent changes, browser upload failure/retry, native unsaved-link/
back/refresh acceptance, remaining mobile framing/keyboard checks, other browsers,
real private storage/CDN configuration, staging/load and release remain unproved.
Earlier unchanged backend/type-specific checks are retained rather than repeated
or replaced by this case. Whole V2 remains active and release remains unverified.
