# Seller Billing populated backup and database recovery — 2026-10-18

Whole original Seller Billing/payOS goal remains ACTIVE / NOT_READY. No Social work resumed, real payment, activation, remote write, commit, merge, deployment or existing/recovered database mutation.

## Current full gate

Legacy-shadow full59846 exited0 at08:21:44ICT:1026reported/1025PASS/1HotQuerySKIP/0failures/errors8m18s. `.local/billing-legacy-shadow-full-verify-summary.json` independently sums Surefire XML before subsequent focused runs. Required SSR/native Billing browser opt-ins ran; previous intermittent SSR failure is historical, with root cause still unestablished. New recovery test extensions below were not compiled in that full gate.

## Populated backup evidence

Extended the existing canonical `PostgresBackupRestoreTest`, not the production backup scripts or application. Focused `.local/billing-populated-backup-restore.log`:1PASS34.23s, Maven exit0/45.690s at08:23:18ICT.

Owned fresh PostgreSQL18/PostGIS latestFlyway34 contains seeded PAID/PENDING_PAYMENT/REFUNDED invoices, payment attempts, active/revoked grants, subscription, consumed quotes, reconciliation observation, reminder bookmark, webhook inbox, finance request and completed idempotency record. Status fixtures are seeded history, not evidence of a real provider payment or bank refund.

Actual repository backup/checksum/restore scripts create a dump and restore a separate empty database. All nonstatic public-table full-row JSONB snapshots match before runtime; all table counts match including static spatial rows. Flyway validates and reruns with zero migrations. Two fully closed/recreated Spring application contexts on the restored database issue real JWT and read user/address plus exactly three expected invoice IDs/statuses/amounts through authorized HTTP, checking no-store. Financial, membership, audit, outbox and idempotency snapshots remain unchanged after each runtime. Authentication session writes are expected and not falsely described as zero database writes. Source database full snapshots remain unchanged.

Existing malformed archive/missing checksum/tampered checksum/nonempty target/production-name/no-confirmation refusal checks still execute. This proves local PostgreSQL ledger restoration and application-context restarts, not separate-JVM restart, object storage backup, production RPO/RTO or provider disaster recovery.

## Actual database death evidence

Actual-JWT HTTP parameterized test interrupts an in-flight signed webhook transaction at two observed SQL lock boundaries: invoice lookup before mutation and final outbox INSERT after invoice/attempt/grant/subscription/inbox/audit writes. It SIGKILLs the owned PostgreSQL container, confirms stopped state and refused JDBC connection, restarts the same container/database, checks full six-financial-table/audit/outbox rollback and invokes the real reconciliation scheduler/service with signed loopback provider GET. Each yields exactly one PAID invoice, successful attempt, grant, settlement audit and outbox event; a second worker pass and late signed duplicate callback preserve financial snapshots and single audit/outbox. No production changes inferred.

Initial fixture exposed Docker dynamic host-port reassignment after restart: API retained52907 while restarted container mapped52929. Actual PostgreSQL WAL recovery completed, but fixture reconnect address was invalid. Initial run1ERROR/2m14s, not a product regression. Only private test-container binding reserves a stable loopback port; first single-boundary rerun1PASS23.23s/34.745sMaven. Expanded two-boundary fixture initially1PASS1FAIL because ACCESS_EXCLUSIVE also blocked JPA merge SELECT before expected INSERT. Source-confirmed correction uses SHARE table lock (reads permitted, INSERT blocked), not an application fix. Final `.local/billing-db-crash-recovery-both-boundaries.log`2PASS24.07s/Mavenexit0/35.481s08:29:16ICT. XML preserved separately. No invented combined aggregate with previous runs.

BIL023 accepted within this actual local scope; original matrix25/30, not whole-goal completion. Combined affected HTTP/backup classes with native manual-reconciliation browser regression are running in `.local/billing-populated-recovery-affected-classes.log`, session83783. Poll same handle; no Maven overlap or predicted counts. Final35-file snapshot refreshed after SHARE-lock correction: `.local/billing-populated-recovery-source-manifest.json` and source-check JSON show35checked/0delta. Gitleaks8.30.1 exit0/no findings894786bytes `.local/billing-populated-recovery-security-final.log`; no allowlist change, not full CVE/image/CodeQL/security completion. `git diff --check` exits0.

## Affected-class final gate

Session83783 terminal exit0 at08:32:12ICT:47PASS/0skip/0failures/errors2m44s `.local/billing-populated-recovery-affected-classes.log`. Independent selected-class XML preserved in `.local/billing-recovery-affected-summary.json`:BillingAuthorization46PASS130.317s, canonical backup1PASS19.292s. This includes both actual DB-crash boundaries in the shared HTTP suite and native manual-reconciliation browser. Latest native `.local/billing-browser-1792287084019/result.json` completedTRUE/pageErrors0. No app source changed; no new whole-frontend or full-BE rerun is claimed. Owned PG/API/Next exited; no listeners3000/8081 at final inspection, only transient owned Ryuk cleanup container remained.

This final result supersedes the preceding running83783 observation. Final source35SHA/0delta and secret scan remain current. Overall25/30 original BIL, not complete;008/025/028/029/030 and operations/security/pilot remain.

Next action: Prove concurrent same-key owner checkout and resolve the original BIL008 historical-body evidence boundary, then finish remaining original requirements.
Required from you: Nothing
Completion condition: Actual exactly-once recovery evidence plus complete original-scope local acceptance; no invented provider or production completion.
