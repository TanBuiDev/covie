# Seller Billing invoice CSV and privacy — 2026-10-18

Whole original Seller Billing/payOS goal remains ACTIVE / NOT_READY. Original matrix26/30 before this capability's full gate. No Social work resumed, real provider payment, normal flag activation, existing/recovered DB write, branch, commit, remote write, merge or deployment. Missing original025/028/029/030 and security/operations/pilot requirements remain intact.

## Scope and actual missing behavior

Approved original Admin invoice CSV requirement and BIL029 were not implemented by existing JSON/status endpoints. `docs/superpowers/plans/2026-10-18-billing-invoice-csv.md` received read-only plan review and a transport amendment review; both Approved, not test evidence.

Actual issuedJWT/HTTP/PostgreSQL route RED `.local/billing-invoice-csv-http-red.log`:1FAIL200expected404actual0errors32.517s17:53:26ICT. Existing finance-role assignment and valid fixture ledger succeeded before the missing-route assertion.

## Minimal server changes and focused proof

- New `GET /api/v1/admin/billing/invoices/export` reuses existing finance-reader authorization, optional merchant filter and bounded page query. Defaults20/offset0; existing limit1..100 and offset bounds retained. UTF8 CSV/no-store/static attachment filename. No unbounded export, new finance permission, payment operation or schema migration.
- Seven columns only:invoiceId/merchantId/invoiceNumber/status/amountVnd/createdAtUtc/paidAtUtc. No user/contact/bank details, financial support notes, provider credentials, payment URLs, QR or allowedActions. CSV doubles quotes, quotes every value and prefixes spreadsheet formula/control-start text. This is serialization proof, not a claim about arbitrary subsequent spreadsheet editing/execution.
- Initial selected8run:7PASS/1HTTPFAIL0errors33.711s `.local/billing-invoice-csv-server-initial.log`. Actual export data, role/privacy/header assertions succeeded; invalid limit101 returned500 instead422 because existing Admin read validation threw IllegalArgumentException into the generic500 handler. Only the two scoped invoice/subscription page exceptions changed to existing VALIDATION_FAILED422; global handling/working valid queries unchanged.
- Final `.local/billing-invoice-csv-server-green.log`:8PASS0skip0fail/errors33.201s17:56:44ICT; actualHTTP1/renderer3/existingfinance-policy4. Real Reader export, denied anonymous/Seller/unassignedAdmin, foreign fixture exclusion, revoked-role existingJWT403, empty next page, all three invalid-page endpoints422, exact financial/audit/outbox JSONB unchanged before revocation. Revocation's legitimate audit is not falsely claimed absent.

## Contract and actual Blob client

Canonical OpenAPI2PASS18.39s/Maven21.304s `.local/billing-invoice-csv-openapi-export.log`; official client generate/check exit0. Independent `.local/billing-invoice-csv-contract-summary.json` removes only the new CSV path and yields exact equality to prior contract:172→173paths,231schemas unchanged. No old path/field meaning changed.

New small Admin export component sits beside Giao dịch gần đây only when existing finance read succeeds. Label `Xuất trang CSV` truthfully represents existing20row page. No request on mount; explicit button, in-flight guard, pending state, structured error/retry, temporary download anchor/object URL removed/revoked. No new styles/dependencies or business actions.

Installed openapi-fetch0.17 defaults JSON parsing. The new component correctly uses existing typed `createApiClient.GET` with parseAs=blob instead of changing the working JSON-only request helper. Existing session/refresh middleware stays in place; non-OK response clone preserves structured errors after client parsing.

UI meaningfulRED3FAIL1.53s `.local/billing-invoice-csv-ui-red.log` against empty new component; not a missing-module compiler assertion. Initial combined6run5PASS/1new-testFAIL `.local/billing-invoice-csv-ui-green.log`:jsdom Blob lacks Blob.text; application download/error/duplicate behavior passed. Only new test uses FileReader to inspect actual Blob contents; affected1PASS2skip1.24s `.local/billing-invoice-csv-ui-final.log`, not a fabricated6green aggregate.

Current fullWeb `.local/billing-invoice-csv-web-full.log`:503PASS12SKIP83.30s, actual full aggregate. Typecheck and targeted3file lint exit0. Production Next16.4 build exit0 compiled3.2s/TypeScript24.6s/38routes `.local/billing-invoice-csv-web-build.log` in isolated Billing worktree, not original Social `.next`.

## Actual combined native/privacy evidence

Focused `.local/billing-invoice-csv-native-privacy.log`:3PASS0skip0fail/errors42.52s/Maven53.293s18:04:46ICT. Actual API/security/JWT/PG/real gateway/owned HTTP provider plus production Next/native Chrome at localhost3000. Includes new CSV HTTP scope test, new poll/callback privacy test and extended real manual-reconciliation browser. Not a real PSP payment, TLS/WAN or production deployment proof.

`.local/billing-browser-1792321469967/result.json` completedTRUE/pageErrors0. Reader keyboard Enter downloads actual CSV blobs at360/390/1366, static suggested filename and strict7columns, bounded5/6/7rows and expected current invoice/amount. Java independently reads each downloaded file and checks reported SHA256. Bootstrap Admin without finance role has no export button. Manual reconciliation still has same-key uncertain retry and exact provider delta3POST/3signedGET; export adds no provider calls. Six final Reader/Operator screenshots viewed; route/unique heading/no page overflow asserted. Existing dense mobile financial tables remain an independent BIL025 legibility/accessibility audit, not automatically passed by this export control.

Privacy test proves exact8field seller-status whitelist, repeated read-only polling/no provider GET, foreign-token/cross-invoice404 and financial JSONB unchanged. Actual valid HMAC callback includes private counterparty markers; forged signature401 leaks none, valid/duplicate responses contain only literal status. Durable inbox/audit/outbox contain no counterparty markers/signature/fixture credentials. Existing signed amount/signature/duplicate tests remain valid on unchanged settlement paths; no speculative privacy refactor was needed.

## Full-source secret scan and provenance

Copied every Git-tracked/nonignored untracked source file:2028files/0missing, manifest `.local/billing-invoice-export-source-manifest.json`. First Gitleaks8.30.1 full-context scan exit1 with7documentation findings, not zero. Redacted-only report metadata retained. Each full matching line is byte-identical to its already-reviewed historical exception in existing `.gitleaksignore`; `.local/billing-invoice-export-gitleaks-classification.json` records file/rule/line/SHA256/existing commit reference, with7/7verified and no secret values printed.

These are the previously reviewed Figma file identifier, internal API URLs/diagram label, sample idempotency UUIDs and ordinary operations checklist wording. No source/CI ignore policy was changed. After verifying every copied source SHA unchanged, a one-use local mapping of precisely those7immutable snapshot file/rule/line fingerprints was generated; second full-context scan exit0/no findings12,952,151bytes `.local/billing-invoice-export-source-security-reviewed.log`. The initial7findings remain documented; this is not an unexplained blanket suppression or a claim of CVE/image/license/CodeQL readiness.

Read-only implementation reviewer reported no concrete security/compatibility defect; no edits/tests by reviewer. New sequential production-source full56817 is RUNNING `.local/billing-invoice-csv-full-verify.log` with SSR/pilot/native-manual opt-ins, after all source changes and focused/browser GREEN. Poll same handle, no concurrent Maven or timeout restart; capture independent XML before any subsequent Maven. Earlier fail-closed full1032PASS1SKIP predates CSV production code, not current final proof. No later application source changes are authorized implicitly by this running gate.

## Next original-scope findings

Read-only Ads source audit found `AdService.active` still includes a grace subscription OR branch while checkout/public read eligibility is READ_ONLY, and targetVisible checks moderation but not linked-target billing. This is a concrete source-risk hypothesis for originalBIL030; reproduce actual delivery/target behavior before any fix, do not invent credit/refund policy. Founder/legacy pricing must follow original approved effective-date policy, not guessed discounts. Keep all original requirements and activation gates.

## Final sequential backend gate

Session56817 terminated exit0 at2026-10-18 18:16:06ICT:1038reported,1037PASS,1HotQuery opt-in SKIP,0failures/errors,7m51s. Independent all-suite XML counts and HTTP XML saved before another Maven invocation at `.local/billing-invoice-csv-full-verify-summary.json` and `.local/billing-invoice-csv-full-http.xml`. Includes SSR/pilot/native-manual opt-ins and the current legacy409/export/privacy code. BIL029 is now accepted locally from its actual scoped privacy evidence plus final gates; this does not establish entire UI/security/operations or real-provider readiness. Remaining original rows025/028/030 and wider local requirements keep the whole goal ACTIVE/NOT_READY.

Next action: Reproduce Ads grace/expired-target behavior and finish remaining original requirements.
Required from you: Nothing
Completion condition: Full original local acceptance, not just CSV/green tests; external provider/real-money steps remain separately evidenced.
