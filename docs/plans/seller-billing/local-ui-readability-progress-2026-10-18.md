# Seller Billing UI readability progress — 2026-10-18

Whole goal ACTIVE/NOT_READY; original28/30PASS after final Ads full1056PASS1HotQuerySKIP0fail/errors9m16s18:53:54. BIL025/028 and wider security/operations/process-restart/pilot acceptance remain open. Backend source is unchanged since that full; UI-only work is not a reason to replay all backend tests. No Social work, normal flag activation, real provider payment, branch/commit/remote write/merge/deploy/recovered-data operation.

## Actual defect and minimal repair

Actual Reader360 and Operator1366 screenshots from `.local/billing-browser-1792322716767` were viewed; mobile financial status/role/identifiers wrap into character fragments. Source uses four `admin-table` classes with no matching style; existing global `admin-data-table` and `admin-table-scroll` already implement the project's readable table treatment. Existing inline overflow alone does not impose a readable table width or keyboard-region semantics.

Approved read-only plan `docs/superpowers/plans/2026-10-18-billing-ui-local-acceptance.md`. New parent component fixtures successfully load actual response shapes before meaningful RED:2FAIL2.06s `.local/billing-ui-readability-red.log`, missing initial status announcement and four named keyboard-focusable regions. Child export/reconciliation are stubbed for parent semantics only; this is not a substitute for their real authorization/native behavior.

Minimal Admin markup: role=status on existing loading paragraph; reuse existing shared classes on all four table/wrapper pairs, uniquely labelled role=region/tabIndex0 and scope=col headers. No new CSS/design system/financial logic/roles/columns/API/source backend change. Affected parent/export/reconciliation8PASS1.55s `.local/billing-ui-readability-green.log`. Independent tsc/targetlint exit0. Production Next buildexit0 compile1.244s/TypeScript5.2s/38routes `.local/billing-ui-readability-build.log`; this changes only the C Billing worktree build, never the original E `.next`.

## Current verification

Full Web70292 RUNNING `.local/billing-ui-readability-web-full.log`; no predicted totals or invented506PASS aggregate. Focused native69319 RUNNING `.local/billing-ui-readability-native.log`, existing actual manual-reconciliation/CSV fixture now verifies twelve table measurements across Reader/Operator360/390/1366. Native Tab from preceding actual button must focus labelled region; ArrowRight must scroll mobile overflow. Status/role Range must remain a single line; actual original-resolution region screenshots retained. Do not assume success or characterize a fixture error as a product failure before logs.

No API production change since final1056full; its current backend evidence is retained, not rerun for markup. Existing canonical contracts/client remain unchanged. Prior scoped Gitleaks scan covered Ads state only, not these subsequent UI files. BIL025 named loading/error/denied/resilience/a11y states must be audited before accepting whole row; improved table markup alone is not whole UI or physical screen-reader certification.

## Final readability results

Full Web70292 TERMINALexit0:506PASS12SKIP,122testfilesPASS1fileSKIP,101.35s `.local/billing-ui-readability-web-full.log`. Focused native69319 TERMINALexit0:1PASS80.01s/Maven1m27s19:00:48ICT `.local/billing-ui-readability-native.log`, independent HTTP XML saved before future Maven. This is the actual full count, not an aggregate assembled from earlier runs.

`.local/billing-browser-1792324825705/result.json` completedTRUE/pageErrors0,12actual Reader/Operator table measurements at360/390/1366. Mobile region widths286/316 with720pixel tables; native Tab then ArrowRight scrolls each mobile invoice/role region. Desktop table and region both984px without required horizontal scrolling. All measured status/role text remains one line. Twenty original-resolution table scroll-position screenshots VIEWED; horizontal viewport crops intentionally show different columns, not every UUID simultaneously. Actual manual-reconciliation/CSV workflow still has exactly3providerPOST+3signedGET and old same-key uncertain retry/no duplicate settlement. No new backend source change or full-BE replay was necessary.

This closes the demonstrated table readability/loading-announcement defect, not all BIL025. Other named error/unauthorized/retry/resilience and screen-reader semantics need explicit coverage/reuse audit; physical screen-reader or device certification is not claimed. Invoice/role tables have actual native layout proof; populated refund/voucher table semantics are component-tested, not falsely claimed as newly native-inspected fixtures. Owned containers/normal3000/8081 absent after terminal.

Final six-file scoped snapshot `.local/billing-ui-readability-source-manifest.json`; Gitleaks8.30.1 exit0/no findings408177bytes `.local/billing-ui-readability-gitleaks.log`. No suppression/allowlist change; this is not full dependency/image/CodeQL acceptance. Final `git diff --check` exit0 aside from existing generated-client CRLF warning; detached HEAD41abfce unchanged/no commit.

Next action: Audit and prove remaining named BIL025 states, then BIL028 missing price notice and remaining original security/operations/pilot requirements.
Required from you: Nothing
Completion condition: Entire local original goal acceptance with actual evidence, not only a table change or green backend suite.
