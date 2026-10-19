# Social V2 — Seller-visible moderation reason code

## Approved requirement and scope

Plan G2.3 requires actual moderation status and reason according to Seller permission. Previous audit established that Seller detail had status only, generic moderation notifications contained no reason, and using an Admin endpoint would disclose more than needed.

Add one optional, read-only `moderationReasonCode` to the existing authenticated `CatalogItemManagementDetailResponse`, not a new route or permission. It is populated only with the V2 caption authoring gate ON and existing MANAGE_CATALOG_STRUCTURE permission (OWNER/MANAGER, matching existing moderation notification recipients). STAFF's existing detail read remains allowed but the new reason is absent. Merchant/item access remains the original server policy.

For current HIDDEN/REJECTED item status, return the latest actual matching HIDE/REJECT decision reason_code for that same CATALOG_ITEM, ordered by decision created_at/id. Return no reason for other statuses or missing decisions. Do not use a case-opening/user-report reason, Admin note, reporter identity, unrelated target or action/case internals. Flag OFF adds no populated value.

## Minimal implementation

- DTO: one optional read-only string/maxLength60 OpenAPI property, consistent with existing Admin reason validation/storage.
- Existing management detail service: scoped parameterized JDBC query with LIMIT1 only for eligible role/status/flag; no mutation or per-row query loop. No migration/index/refactor or broader access-policy change.
- Edit form: renders only the supplied generated-type code as escaped literal text in existing warning styling, behind the existing web authoring gate. No fabricated reason or free-form HTML. Existing status, draft, commerce/actions remain unchanged.

This is completion of the approved missing feature, not a claim that the previous BE behavior was generally broken.

## Evidence

### RED

New `issuedSellerReadsOnlyCurrentDecisionReasonWithinCatalogManagementScope` makes actual issued-JWT Admin HIDE and reads the actual Seller detail. It fails because `$.moderationReasonCode` does not exist: `.local/social-v2-g2-seller-reason-red.log`, Maven35.977s. UI RED separately finds no reason note: `.local/social-v2-g2-seller-reason-ui-red.log` (ON fails, unchanged OFF passes).

### BE, privacy and readonly checks

Sequential isolated API copy/disposable PG:

```text
mvnw.cmd -o -Dtest=MerchantCatalogEditControllerIntegrationTest#issuedSellerReadsOnlyCurrentDecisionReasonWithinCatalogManagementScope,CatalogCaptionFeatureDisabledIntegrationTest,OpenApiExportTest#sellerDecisionReasonIsAnOptionalReadOnlyCode+exportsOpenApiDocument -Dcovie.openapi.output=E:/WebApp/CoVie/packages/api-client/openapi/openapi.json test
```

`.local/social-v2-g2-seller-reason-green.log`: **4 PASS**, Maven46.639s. Covers:

- Both actual Admin HIDE and REJECT with decision code SPAM and private-note sentinel. OWNER and MANAGER issued JWT detail reads200/code SPAM.
- Different case-opening reason and newer foreign-target decision/note sentinels never appear. STAFF200 without reason, foreign merchant404, guest401, no-store header.
- Before/after read sequence, full JSONB of catalog_items/variants/location_variants/catalog_item_media/moderation_cases/moderation_actions/audit_logs/outbox_events unchanged.
- Actual RESTORE for either case then Seller detail without the old reason.
- Flag-OFF fixture has HIDDEN status and an actual stored HIDE decision but does not expose the new code; old create/patch assertions still pass.
- OpenAPI optional string/readOnly/maxLength60 and existing export assertions.

Then strengthen only the new HTTP test with actual RESTORE→new case HIDE using UPDATED_REASON; read returns the new decision rather than historical SPAM. `.local/social-v2-g2-seller-reason-final.log`: **1 PASS**, test22.23s, Maven33.313s. Production/schema code was unchanged; unrelated tests/export were not repeated after this test-only extension.

### Contract, client and UI

- Canonical before/after comparison: all **147 paths** and **211 schemas** unchanged except this one optional property. Evidence `.local/social-v2-g2-seller-reason-contract-diff.log`. The comparison removes only that property in memory, not from the exported file; required fields and all other components must still match.
- Generated API client and its typecheck pass: `.local/social-v2-g2-seller-reason-client.log`. The generated field is `readonly moderationReasonCode?: string`.
- `.local/social-v2-g2-seller-reason-ui-green.log`: **35 PASS**, 8.22s, changed edit form's targeted file. Includes literal HTML-looking reason/no script node, flag OFF omission, existing caption/gallery/feedback/draft/permission/command regressions.
- Final web typecheck exits0. Targeted ESLint exits0 with one existing legacy img warning. Corresponding `-typecheck.log` and `-lint.log`; diff check passes (Git generated LF/CRLF notice is not a source error).
- Source/private-copy DTO and query service hashes match. No full BE/web suite or unrelated race rerun. No runtime/recovered DB access, normal process restart, activation, commit/branch/remote operation or deployment. Normal web/API PIDs7544/25008 remain alive.

## Remaining gates

Readonly HTTP plus mocked React transport does not prove actual Seller Next/browser display, four viewports, account changes, concurrent decision/read snapshots, heavy case-history SQL plan/load, or staging. The query is scoped/LIMIT1 but no representative query-plan performance claim is made; do not add an index without measuring.

Whole V2 remains ACTIVE, normal V2/video flags OFF, staging/release UNVERIFIED. Next: a focused real Seller browser scenario for the reason and lazy feedback entry using existing isolated fixtures; preserve readonly business snapshots and do not repeat the existing create/gallery happy path.
