# Seller onboarding, days remaining and existing-subscriber renewal

Whole Seller Billing goal remains ACTIVE / NOT_READY. This is local evidence, not real payOS, physical-device/screen-reader, legal, staging, release or production acceptance. Owner-approved legacy409 behavior remains unchanged. Normal money/release/reconciliation/enforcement flags stayOFF; no commit/remote write/merge/activation/deploy or recovered-data mutation.

## Onboarding and remaining days

Reviewed plan `docs/superpowers/plans/2026-10-18-billing-onboarding-days.md`, original9.2. Existing MerchantForm validation/live preview and server payment-only navigation remain. The existing server policy disclosure now sits inside the actual new-shop onboarding form, not duplicated externally. Generated Onboarding type replaces the old local DTO. Four ordered labelled steps, required controlled acknowledgement, disabled while policy unavailable/registration denied, explicit submit guard and GET-only policy retry. This acknowledges displayed subscription conditions, not an invented legal document/consent record or VAT/provider approval. Ordinary create/edit contexts do not gain this requirement.

New form regression first1FAIL5filteredSKIP6.80s `.local/billing-onboarding-consent-red.log` (absent checkbox), then affected9PASS6.24s. New days component missing export1FAIL3filteredSKIP1.19s `.local/billing-remaining-days-red.log`; separate real missing periodEnd fallback1FAIL17filteredSKIP7.14s `.local/billing-period-days-red.log`. Minimal advisory ceil24h periods from server eligibility expiry or actual subscription.periodEnd; no inference from status, negative days, changed allowedActions, charge or entitlement. Future server period does not override canSell=false. Pending server-verification message directs to refresh/invoice support reference, no invented SLA/contact.

Final focused32PASS17.00s `.local/billing-onboarding-days-final-focused.log`. Typecheck exit0; scoped lint0errors/4preexisting unused-import warnings (CheckCircle/MapPinLine/PlusCircle in Seller page, Check in MerchantForm), no suppressions/unrelated fixes. Current production build compile2.7s/TypeScript5.4s succeeds `.local/billing-onboarding-days-build.log`. Full Web62936 TERMINAL539PASS12SKIP100.84s `.local/billing-onboarding-days-web-full.log`, not combined with preceding535 run.

Native73469 TERMINAL1PASS95.50s/Maven1m39s22:21:53ICT `.local/billing-onboarding-days-native.log`; fixture `.local/billing-browser-1792336863868/result.json` completedTRUE/errors0 Chrome155.0.8059.84. All360/390/1366: browser transport injects one policy503 (not a backend outage), actual form remains disabled; nativeEnter retries actual serverGET200, nativeSpace acknowledges, actual createPOST201. Actual Admin-created price, Seller checkout, signed loopback PAID/duplicate, server-period days, cancel/resume and Admin UI still complete. Java checks persisted single settlement/grant per shop. New merchant creation itself does not imply paid entitlement. All three onboarding screenshots and desktop days screenshot VIEWED, no page overflow; full-page tall mobile captures are not physical-device proof. Original E.next never used/rebuilt.

## Existing subscriber and announced effective pricing

Reviewer correctly identified that previous tests proved new-shop effective price, renewal calendar and notices separately, not the combined old subscriber renewal. No product defect was assumed. Added exactly one existing actual-JWT/HTTP/PostgreSQL test `existingPaidSubscriberRenewsAtNotifiedEffectivePriceWithoutRewritingPaidHistory` to the established BillingAuthorizationHttpIntegrationTest; no production code changes.

Actual owner buys/settles10000, Admin schedules15000 revision2, real outbox handler sends that existing owner the version notice, financial snapshot remains identical through publication/notice. Once effective, same existing shop quote/checkout uses15000/revision2/exact new version ID; renewal starts at its previous paidThrough and ends one UTC calendar month later. Full prior paid invoice/attempt/grant JSONB remain identical after renewal settlement; exactly two provider creations total. This is loopback signed-provider evidence, not real bank payment or invented founder discount/cutoff.

Focused59458 TERMINAL1PASS30.18s/Maven42.252s22:25:42ICT `.local/billing-existing-subscriber-effective-renewal.log`. Existing current production-source full1058reported1057PASS1HotQuerySKIP9m24s remains valid for production source, but predates this additional test: do NOT invent1058PASS aggregate. Prior six early calendar cases, late renewal, legacy/shadow configured grants and101-owner/two-page notice tests remain unchanged/full-verified. A11 requires scheduled advance notice/nonretroactive, not guessed preferential founder pricing.

## Scope and next work

Original BIL025 local UI/state/keyboard/accessible semantic coverage and BIL028 local version/renewal coverage can now be accepted with the explicit evidence above and prior named-state/QR reports. Whole plan remains incomplete: separate-JVM crash/restart, final security/performance opt-in disposition and operator real-money pilot preparation still need closure. Existing DB SIGKILL/WAL recovery and two Spring contexts must not be renamed new-JVM proof. No repeated completed Ads/CSV/Buyer/native checks without relevant source change.

28-file current presentation source snapshot861686bytes Gitleaks8.30.1 exit0/no findings `.local/billing-onboarding-final-gitleaks.log` predates only the last added Java test; refresh that delta before final source claim. No CVE/image/CodeQL claim. Owned native processes/containers exited; normal3000/8081 empty.

Next action: Prove separate packaged API JVM crash/restart with the same owned database and close remaining local security/performance/operator gates.
Required from you: Nothing
Completion condition: Whole original plan local evidence is complete; real-money/provider actions remain explicit authorized operator boundaries, not fabricated passes.
