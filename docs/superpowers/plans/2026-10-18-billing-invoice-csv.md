# Billing Invoice CSV Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox syntax for tracking. Existing reviewer can review this bounded chunk; preserve shared files and never overlap Maven.

**Goal:** Implement the explicitly required P0 invoice CSV export and prove BIL029 privacy/permission behavior without broadening financial access.

**Architecture:** Add one read-only representation backed by the existing authorized, filtered, bounded Admin invoice query. Export only its existing non-PII invoice fields; reuse current finance READER policy and merchant filter/page semantics. A small Admin client component downloads only after a human click and preserves existing invoice loading/business behavior.

**Tech Stack:** Spring Boot/Java25/JdbcTemplate, existing OpenAPI generator, Next/React19, Vitest, actual JWT/HTTP/PostgreSQL and owned native Chrome fixture.

Execution closed for this bounded capability: actual HTTP/renderer/API contract/client/UI/native/privacy and final full56817 all passed,1037PASS1HotQuerySKIP7m51s18:16:06ICT. Original BIL029 accepted; report `local-invoice-csv-privacy-progress-2026-10-18.md` records source scan initial seven previously-reviewed documentation findings plus exact verified local snapshot mapping, not blanket suppression. No commits. Subsequent Ads changes require their own gates; this is not whole-goal completion. Detailed checkboxes below are the original execution instructions, with this final evidence authoritative.

No commits, branch creation, remote writes, rollout, normal flags or existing/recovered database changes. This plan is one remaining original-goal capability, not replacement acceptance for all BIL029 or whole Billing. Current fail-closed full35583 is already running; collect terminal output and preserve XML before Maven for this chunk. Do not change current production source before that full gate ends.

## Chunk 1: Bounded non-PII invoice representation and UI

Plan review: existing read-only reviewer approved this chunk on2026-10-18 with no concrete blocking defects; no tests run or implementation acceptance implied. While full35583 is already compiled/running, only the future actual CSV HTTP test has been added to source, not production code. This new test is not covered by that full invocation and changes its prior test-file snapshot; unchanged production hashes remain the gate boundary.

Source-specific validation finding: existing Admin read pagination throws IllegalArgumentException, while GlobalExceptionHandler maps uncaught exceptions to500. The new HTTP test will require422 for invalid CSV and existing invoice/subscription page limits. Only after an actual failing response proves this risk, minimally replace those two scoped validation exceptions with existing VALIDATION_FAILED422; do not modify global exception handling or unrelated APIs.

### Task 1: HTTP contract RED and server representation

**Files:**
- Modify `apps/api/src/main/java/vn/covie/billing/api/AdminBillingReadController.java`.
- Create `apps/api/src/main/java/vn/covie/billing/api/BillingInvoiceCsv.java`.
- Extend `apps/api/src/test/java/vn/covie/billing/api/BillingAuthorizationHttpIntegrationTest.java`.
- Add focused renderer cases at `apps/api/src/test/java/vn/covie/billing/api/BillingInvoiceCsvTest.java`.

- [ ] Add actual HTTP test before implementation: `GET /api/v1/admin/billing/invoices/export?merchantId={ownedFixtureMerchant}&limit=20&offset=0`. Authorized finance READER200 CSV; missing actor401; Seller/foreign Seller403. Existing finance-role enforcement/revocation is reused, not inferred on UI.
- [ ] Observe new route404 RED after current Maven ends. Assert filtered fixture invoice only, all expected header/values, no other merchant invoice, user email/phone/name, payment link/QR/provider credential fields or support reason. Protect full financial/audit/outbox snapshots against read writes; authentication session bookkeeping is not falsely claimed absent.
- [ ] Implement endpoint with `@GetMapping(value="/invoices/export",produces="text/csv")`, optional merchantId/defaultlimit20/defaultoffset0 identical to existing list. Call `data.invoices(merchantId,limit,offset)` before rendering, reuse its1..100/maxoffset validation and current role guard. Response200 `text/csv;charset=UTF-8`, no-store, static attachment filename `seller-billing-invoices.csv`. Existing security supplies nosniff.
- [ ] Render exactly `invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc` in that order with CRLF rows; UTC ISO instants and empty nullable paidAt. No names, contact details, raw PSP references, checkout links, QR, bank details, finance notes or allowedActions.
- [ ] Minimal renderer uses existing `AdminBillingReadService.Invoice` rows only. CSV values are always quoted, embedded quotes doubled. For formula-injection safety prefix apostrophe when first character is tab/CR/LF or the first non-whitespace character is one of `=+-@`; preserve original remaining text. IDs and integer VND are server-owned values, not browser-supplied money.
- [ ] Unit tests cover quote/comma/line-break escaping, actual dangerous prefix fixture, empty list header, null paidAt. HTTP test invalid limit101 and cross-role/revoked access uses existing error behavior without adding a permissive export-specific path.
- [ ] Run focused new HTTP/renderer tests plus existing Admin read/permission units. Expect all selected cases pass; do not claim real provider acceptance.

### Task 2: Canonical contract and Admin download

**Files:**
- Canonical `packages/api-client/openapi/openapi.json` and generated `packages/api-client/src/generated/schema.ts` via official scripts only. Existing `OpenApiExportTest` accepts `covie.openapi.output`.
- Create `apps/web/features/admin/admin-billing-invoice-export.tsx` and `.test.tsx`.
- Modify only export placement in `apps/web/features/admin/admin-billing-screen.tsx`.

- [ ] Export OpenAPI with existing `OpenApiExportTest`; run `pnpm api-client:generate`, `pnpm api-client:check`. Inspect semantic diff: only new read-only CSV path, no old path/schema meaning changed. Generated response documents CSV string; do not hand-edit generated files.
- [ ] Write component test RED: explicit keyboard/click export triggers authenticated GET to new generated path with existing displayed page20/offset0, one blob download/static filename, object URL revoked and temporary anchor removed. Pending request prevents repeated downloads; error remains a labelled alert with retry; no request on mount. Do not render an export control in existing financeForbidden branch.
- [ ] Implement minimal client component with existing `createApiClient` typed GET/parseAs=blob, `readApiError` on a captured non-OK response clone, in-flight ref/state, existing admin button classes, native button and aria-busy. Successful fetch creates blob URL, temporary download anchor, clicks then removes/revokes in finally; clears ref/state even on error. No sensitive token in URL/localStorage, no new dependency or new design system.
- [ ] Place control beside existing Giao dịch gần đây section, label `Xuất trang CSV` so a20row page is not misrepresented as full-history export. Existing zero-row state remains intact; do not add a new unapproved business filter or unlimited export.
- [ ] Run targeted Vitest, typecheck and lint; then required frontend full gates for production UI changes, with no unrelated fixes. New browser verification reuses owned production Next/API/PG fixture, finance Reader/denied role, actual downloaded CSV at360/390/1366 and route-unique landmark. Review screenshots only if layout changes visibly; download/permissions proof is distinct from pixels.

### Task 3: Privacy acceptance, source provenance and handoff

- [ ] Inspect actual existing poll/status and callback response fields with signed fixture inputs, cross-merchant and role boundaries. Reuse unchanged signature/amount/replay tests where they already prove the requirement; add only missing assertions. CSV path is not a substitute for the other BIL029 surfaces.
- [ ] After focused green and final source changes, run sequential full Maven verify with current SSR/pilot/native opt-ins; capture independent XML counts/skips and no guessed totals. Never run concurrent Maven against shared target.
- [ ] Source snapshots and Gitleaks cover actual new/changed files; verify generated-client drift, `git diff --check`, cleanup of owned processes/data. No scanner suppression or broad secret-scan completion claim.
- [ ] Update rootAGENTS, original BIL029 row and a progress report with exact final evidence and remaining limits. Keep full goal active until all other original local requirements and security/operations/pilot preparation are resolved; external provider/real-money boundaries remain explicit.

### Exact validation commands

From `apps/api` after the current full run ends, with the existing Java25/Maven environment:

```powershell
./mvnw.cmd -o '-Dtest=BillingAuthorizationHttpIntegrationTest#adminInvoiceCsvExportIsBoundedScopedAndPrivate,BillingInvoiceCsvTest,BillingFinanceAccessPolicyTest' test
./mvnw.cmd -o '-Dtest=OpenApiExportTest' '-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json' test
```

RED expects the actual new HTTP route404, not an invented compiler failure. Add renderer test only after the HTTP RED, then verify all selected cases GREEN. New test names are planned concrete additions, not a claim these already exist. From repository root:

```powershell
pnpm api-client:generate
pnpm api-client:check
pnpm --filter @covie/web exec vitest run features/admin/admin-billing-invoice-export.test.tsx
pnpm web:typecheck
pnpm --filter @covie/web exec eslint features/admin/admin-billing-invoice-export.tsx features/admin/admin-billing-invoice-export.test.tsx features/admin/admin-billing-screen.tsx
```

### Core server implementation to use after RED

Existing controller method, no new service authorization path:

```java
@GetMapping(value = "/invoices/export", produces = "text/csv")
public ResponseEntity<String> exportInvoices(
        @RequestParam(required = false) UUID merchantId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset) {
    String csv = BillingInvoiceCsv.render(data.invoices(merchantId, limit, offset));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header("Content-Disposition", "attachment; filename=\"seller-billing-invoices.csv\"")
        .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv);
}
```

New focused representation class:

```java
package vn.covie.billing.api;

import java.util.List;
import vn.covie.billing.application.AdminBillingReadService;

final class BillingInvoiceCsv {
    private BillingInvoiceCsv() {}

    static String render(List<AdminBillingReadService.Invoice> invoices) {
        StringBuilder csv = new StringBuilder("invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n");
        for (var invoice : invoices) {
            String[] fields = {invoice.id().toString(), invoice.merchantId().toString(),
                invoice.invoiceNumber(), invoice.status(), Long.toString(invoice.amountVnd()),
                invoice.createdAt().toString(), invoice.paidAt() == null ? "" : invoice.paidAt().toString()};
            csv.append(java.util.Arrays.stream(fields).map(BillingInvoiceCsv::cell)
                .collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        return csv.toString();
    }

    private static String cell(String value) {
        String stripped = value.stripLeading();
        boolean formula = !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0;
        boolean control = !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0;
        String safe = formula || control ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
```

Download component must use the response blob unchanged, not build CSV from browser state; this keeps server-side scope authoritative. In-flight state/ref must be set before first await. Download is explicit-user initiated, temporary object URL always revoked and anchor removed; no persistent copy in application state or localStorage. Existing Admin classes and placement are reused rather than a new visual design.

Transport correction from actual installed source: openapi-fetch0.17 defaults parseAs=json (`node_modules/.pnpm/openapi-fetch@0.17.0/node_modules/openapi-fetch/dist/index.mjs:30,188,192`), and `requestBrowserApi` does not override it. Do not feed a CSV response to that JSON-only helper or modify working JSON consumers. The new component must instead call existing `createApiClient` typed `GET` for the generated export path with `parseAs:"blob"` and typed limit/offset query. Keep credentials/CSRF/session-refresh middleware. Capture a cloned non-OK response through existing observer if needed for `readApiError`, since parsed errors consume the original body. Tests and actual browser download must prove real blob handling, not a JSON-body mock masquerading as CSV.
