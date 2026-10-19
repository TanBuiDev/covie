# Seller Billing — public entitlement and Buyer/Social history

Whole original Seller Billing/payOS goal remains ACTIVE / NOT_READY. Original BIL001/019 now have scoped local evidence; BIL018 remains incomplete until its full performance/no-N+1 requirement is measured. Normal payment/release/reconcile/enforcement flags remain OFF. No real PSP, transfer, remote Git write, commit, branch, deployment, normal-stack restart or recovered-data operation.

## Starting full gate and unchanged Order failure

Previous continuation made actual progress: repaired the trial test schedule and verified the actual Admin registration switch. Its sequential full gate is now terminal: `.local/billing-calendar-registration-full-verify.log`,exit1,1006reported/1004PASS/1FAIL/zero errors/1SKIP,8m47s. Independent XML summary `.local/billing-calendar-registration-full-verify-summary.json` was saved before narrow tests overwrite reports. Billing HTTP26cases allPASS; `.local/billing-calendar-registration-full-http.xml` preserves them. Native `.local/billing-browser-1792264774237/result.json` completed=true/ICT=true/pageErrors0.

The sole failure is unchanged Order cancellation/race test repeat2: its dispatch helper leaves one unpublished outbox row after200 attempts. Exact XML `.local/billing-calendar-order-race-full.xml` is preserved. The unchanged repeated method then executes all three repetitions successfully within `.local/billing-public-entitlement-red.log` (36.54seconds). Root cause remains unestablished; no Order production/test fix, waived assertion, manually green full status or invented aggregate is claimed. The sole configured hot-query-plan skip is not a pass or a demonstrated external blocker.

## Confirmed public-read defects

New actual issued-JWT/HTTP/Spring/PostGIS/fake-provider cases execute in the isolated Billing fixture. Existing paid grants establish a positive control, then fixture-only term changes put the merchant into actual evaluated READ_ONLY grace or EXPIRED. No actor, entitlement, clock or discovery mock is used.

`.local/billing-public-entitlement-red.log` TERMINALexit1:10cases/6PASS/4FAIL/zero errors/skips,1m17s. Seven new Billing cases produce three passes and four meaningful failures; the unchanged Order race contributes three passes.

- Search and Shop each return a priced offer during grace even though the actual billing summary says `canSell=false/GRACE_PERIOD_READ_ONLY` and public Detail returns404. The two SQL repositories independently allow an expired grant through a grace subscription branch, contrary to the approved READ_ONLY rule.
- Buyer Need response reads keep `available=true/actionable=true` for both grace and fully expired merchants. Actual create-response commands already check entitlement, but the response-reader computes availability only from catalog/location/stock/moderation.
- Full expiry Search/Shop controls already pass; Buyer/Social/history case already passes before production fixes. These working paths are not rewritten.

## Minimal production correction

- Remove only the grace-selling OR branch from `JdbcCatalogDiscoveryRepository` and `JdbcPublicMerchantCatalogRepository`. Enforcement OFF and currently valid unrevoked grants retain their existing paths. No new per-item lookup is added to either public SQL query.
- Add internal `BillingEntitlementService.merchantsAllowedToSell(Set<UUID>)`: enforcement OFF preserves the requested set; ON resolves current unrevoked grants in one bounded set query; empty pages require no grant query. This is internal service code, not a new REST API.
- `NeedResponseReadService` resolves the page's distinct merchants once and combines that result with existing availability predicates. Its single merchant-response reader uses the existing evaluator. Historical response rows, prices, IDs and existing moderation/stock rules stay unchanged. Legacy standalone constructors remain compatible; production Spring wiring supplies the real service.

No migration, API field, generated client, frontend, price, retention, auto-payment or moderation-state change. Four production files:33insertions/25deletions. This fixes billing correctness without claiming to optimize the entire pre-existing Need reader.

## Focused final proof

`.local/billing-public-entitlement-green.log` TERMINALexit0:79PASS/zero failures/errors/skips,1m29s:

- Seven new Billing HTTP cases pass (25.72seconds): Search/Shop grace and expired filtering, corresponding Detail404, no financial/commerce mutation; Need retains its historical response but flags it unavailable/nonactionable and rejects a fresh response command409, preserving Need/response JSONB and financial rows.
- Five existing public Discovery cases, six existing merchant catalog cases and61existing Need eligible/response cases pass unchanged. These include enforcement-OFF/default behavior in their existing fixtures. No whole frontend or unrelated baseline suite is rerun for test-only changes.
- Original BIL001: a USER with no merchant membership/subscription browses alongside a guest, performs authenticated LIKE/SAVE/comment, adds a cart item, commits actual CART checkout, completes the order and writes a verified review. Six financial-table JSONB snapshots remain unchanged through Buyer activity; fake-provider create count stays one for the Seller's prior payment.
- Original BIL019: after fixture expiry and actual `canSell=false`, full JSONB snapshots of nine Social/review/order/catalog/merchant tables remain identical. Buyer sees their saved/liked state; guest comment/review readers return the same IDs; old order remains readable. A test-only HIDDEN comment fixture is excluded by actual readers without further row mutation. This is moderation-read proof, not an Admin moderation-command rehearsal.

Original matrix20/30PASS; BIL018 still NOT_TESTED with substantial partial proof. Its exact no-N+1 requirement is not established merely by single-query source structure. Existing Need DTO loading still performs per-response/item repository calls; a measured endpoint/query-count acceptance audit remains required before accepting that row. Do not silently narrow BIL018 to only the new Billing batch query.

## Provenance, security and next full gate

Nine source files have matching owned copies/SHA256 in `.local/billing-public-source-manifest.json` (previous calendar/trial source plus current four production files and updated HTTP tests). Gitleaks8.30.1 delta scan exit0/no findings,185159bytes, `.local/billing-public-security.log` and redacted JSON. No ignore/allowlist change. `git diff --check` exits0 with the existing generated-schema line-ending warning.

This is not a full dependency/image/CodeQL security PASS. The previously recorded dev-only braces3.0.3 HIGH remains unresolved; the current [GitHub-reviewed advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) still lists versions through3.0.3 affected and no patched version. Local prior audit identifies only the ESLint/fast-glob/micromatch dev path. No speculative dependency replacement, scanner suppression or risk waiver is made; runtime applicability and final security acceptance remain open.

New sequential full verify is running in `.local/billing-public-entitlement-full-verify.log`,with SSRbrowser/pilot/calendar-native opt-ins. It is justified by the four confirmed production-path corrections; no overlapping Maven and no predicted result/count. Previous1006full results predate these production corrections and are historical. Existing frontend source/client/formatter build evidence is unchanged; no fresh frontend result is invented.

Next action: Finish the current full backend gate, then measure the remaining public/Need no-N+1 acceptance and continue the other original financial/recovery/security/UI requirements.
Required from you: Nothing
Completion condition: Current full evidence is terminal and each remaining original local requirement has adequate measured evidence, not only source or a passing subset.
