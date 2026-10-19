# Seller Billing local checkout replay evidence — 2026-10-17

Whole goal remains **ACTIVE / NOT_READY**. Execution checkout is the managed detached feature checkout at `41abfce04276950db2b680d44cd1b6daad0106e3`. No commit,push,merge,deployment or real payment occurred.

Later continuation/results supersede pending-process notes here:see `local-settlement-progress-2026-10-17.md` for the terminal isolated-pilot full gate and subsequent signed settlement/audit work.

## Actual defect and minimal correction

Existing invoice lookup already precedes quote validation. Consuming/expiring a quote does not itself invalidate same-request replay. The confirmed defect was the fingerprint:only plan and normalized voucher,ignoring quote identity,expected amount and expected revision. Real PostgreSQL reservation followed by the same key/different quote returned success instead of the original BIL-008 conflict.

New reservations hash version-tagged request fields,including the three omitted fields. Merchant locking,reservation-before-provider commit,money calculations,quote checks,schema and API contract stay unchanged. Historical plan/voucher fingerprints remain accepted so previously issued invoices can still be recovered after quote cleanup without another provider POST. Historical records did not retain the full original request:this compatibility path cannot prove exact equality of their old quote/optional expectation fields. Do not claim full historical changed-body enforcement.

## PostgreSQL evidence

`BillingCheckoutReplayIntegrationTest` uses its own disposable PostGIS18 database,actual Flyway001–033,JDBC and Spring transaction manager. Actor/membership lookup,settings and provider are mocked;not issued-JWT/HTTP authorization,real payOS,signed webhook or fake-HTTP-provider browser proof.

- RED `.local/billing-checkout-replay-red.log`:3 executed,1 passed,2 failed. Changed quote demonstrated missing conflict. The other failure was test equality comparing PostgreSQL microsecond expiry with Java nanoseconds,not a product failure.
- Only the expiry assertion was adjusted to compare all other fields exactly and expiry within1 microsecond.
- GREEN `.local/billing-checkout-replay-green.log`:7 passed,0 failures/errors/skips,29.100s:4 new integration cases and3 existing quote unit cases.
- Verified consumed/expired quote replay;changed quote/amount/revision conflicts for new reservations;one committed invoice/attempt after lost provider response without repeated provider call;historical replay after quote deletion.
- Concurrent checkout,authenticated HTTP,signed settlement/reconciliation and restart remain separate required work. No whole BIL row is promoted from these cases alone.

## Other actual gates

- Baseline full Maven verify:957 passed,4 skipped,0 failures/errors,5m49s (`.local/billing-baseline-verify-online.log`). Predates checkout correction. Current-source full verify with SSR-browser/pilot-fixture opt-ins is separately running;inspect terminal result before claiming success.
- Official OpenAPI export:2 tests passed,client generation completed. OpenAPI exact equality/client newline-normalized equality confirmed against before snapshots (`.local/billing-contract-drift-result.json`);no semantic drift.
- API-client check/Web typecheck passed. ESLint exit0 with28 existing warnings,not warning-free.
- Initial full Vitest:492 passed,1 failed,12 skipped;AdSlot replay saw2 calls instead of1. No Ads source/test changed. Focused rerun5 passed;confirmation full run493 passed,12 skipped,104.36s. Root cause unestablished;do not claim fixed.
- Next production build exit0 (`.local/billing-web-build.log`),not runtime/browser/deployment proof.
- Native Gitleaks8.30.1 all-fetched-history scan,existing ignore file:exit0,0 findings (`.local/billing-gitleaks-history.log`,redacted JSON). Does not cover uncommitted source/image vulnerabilities/CodeQL.
- Additional source-only snapshot scan:1985 nonignored tracked/untracked files,raw exit1 with7 documentation findings. Independently matched each current line byte-for-text with its existing reviewed historical exception using `git show`;7/7 identical (`.local/billing-gitleaks-source-review.json`). No scanner disable,new allowlist or secret content printed. This is reviewed evidence,not a raw green source scan or replacement for the official Trivy source gate.
- Configured pnpm security thresholds passed:production high gate exit0/no known vulnerabilities;all-dependency critical gate exit0 with1 high dev-only finding,not zero vulnerabilities. Detailed audit identifies `braces<=3.0.3` through Web ESLint/Next lint plugin/fast-glob/micromatch (`.local/billing-pnpm-audit-details.json`,GHSA-vfj7-8cjw-p6xm);no dependency upgrade or waiver made. Official Trivy/CodeQL and applicability review remain required.
- Trivy0.72.0 HIGH/CRITICAL misconfiguration+secret scan of the same1985-file source snapshot terminated exit0 (`.local/billing-trivy-source.log`,`.local/billing-trivy/source.json`). Matches configured source-scanner flags,no vulnerability/image scan or CodeQL claim. Subsequent report-only edits are outside that earlier snapshot.
- Current-source full verify with both opt-ins terminated **exit1**,965 reported:963 passed,1 failed,1 skipped,5m49s (`.local/billing-current-verify-optins.log`). SSR browser2 passed,10.84s;checkout integration4 passed. Remaining skip is the environment-driven hot-query-plan test. Pilot fixture failed its global cross-merchant offer-count assertion:expected0,actual2. Thus this is not a green current-source full gate.
- The unchanged pilot fixture passed alone:1 passed,39.856s (`.local/billing-pilot-fixture-isolation.log`). It inherited the same database as earlier nontransactional repository fixtures while asserting global exact counts. Test-only correction gives this fixture its own disposable PostGIS database and explicit test profile;keeps all ownership/count/plan assertions and transaction rollback intact. No application SQL/data constraint was relaxed. Corrected focused run1 passed,0 failures/skips,50.113s (`.local/billing-pilot-fixture-owned-db.log`). The complete combined opt-in verify is now running again in `.local/billing-current-verify-isolated-pilot.log`;no green whole-suite claim until terminal result.
- Actual-child launcher regression also passed through existing script entrypoint (`.local/billing-dev-script-regression-final.log`). Fake keys only.
- Original E-drive checkout rechecked:384 file hashes,0 changed. Real-money/release flagsfalse,enforcement not activated,recovered data untouched.

Next action: Inspect current full verify including opt-ins,then complete transaction-safe signed settlement/reconciliation and original BIL scenario evidence.
Required from you: Nothing
Completion condition: Current gates have terminal results and remaining original BIL cases gain scoped evidence;whole goal stays active until full acceptance.
