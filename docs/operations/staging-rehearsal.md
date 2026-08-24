# Staging release rehearsal

A production pilot is blocked until a clean, production-shaped staging rehearsal passes. The rehearsal uses disposable/staging resources only; it never imports production data or points restore/drop operations at production identifiers.

## Required staging resources

- empty PostgreSQL/PostGIS database for the candidate stack;
- separate empty database for restore verification;
- isolated Redis namespace/instance;
- private staging S3 bucket;
- HTTPS staging domain and certificate;
- runtime config matching `prod` safety semantics;
- dedicated Buyer/Seller/Admin staging accounts created through normal Google onboarding.

Plan 10's automation deliberately refuses `https://covie.vn` and requires `COVIE_REHEARSAL_CONFIRM=STAGING_ONLY`.

## Preconditions

The clean database must migrate successfully through V013 (or an explicitly reviewed later migration). `verify-clean-migration.sh` requires a complete successful 1..13 sequence by default. Enable the committed Grand Park reference bootstrap once, verify idempotency, then disable it. Bootstrap the first staging Admin only after normal Google + local email/password onboarding and remove the bootstrap email immediately after the grant.

The current Plan 10 branch does not contain Plan 09's Playwright dependency/fixtures. Therefore `run-staging-rehearsal.sh` requires `COVIE_STAGING_P0_CMD` to be supplied after the Plan 09 prerequisite is merged; the rehearsal is not allowed to substitute a fake in-memory/browser test.

## Full gate

Set staging-only connection variables plus the following command hooks:

```text
COVIE_STAGING_P0_CMD
COVIE_RATE_LIMIT_REHEARSAL_CMD
COVIE_RESTORED_API_SMOKE_CMD
COVIE_ROLLBACK_DRILL_CMD
COVIE_ALERT_REHEARSAL_CMD
```

Then run:

```bash
COVIE_REHEARSAL_CONFIRM=STAGING_ONLY \
COVIE_STAGING_BASE_URL=https://staging.covie.vn \
COVIE_RESTORE_DATABASE=covie_restore_rc1 \
./infra/scripts/run-staging-rehearsal.sh
```

The script verifies Flyway, captures source invariants, runs public smoke/security/load, executes the supplied normal-auth P0 flow and controlled rate-limit/Redis failure checks, creates a logical backup, restores into the second database, compares critical invariant counts, runs a temporary API smoke against the restored database, executes the digest rollback drill and triggers/resolves staging alerts.

## P0 business evidence

The supplied P0 command must prove normal authenticated flows, not `/api/v1/test-support/login`: guest discovery, Buyer Product Order, Seller progress/delivery, Buyer completion, Service flow, Need -> response -> confirmed source Order -> FULFILLED, Review, Admin settings/report read, Outbox drain and notification visibility.

## Rate-limit evidence

Controlled staging tests must show auth/login 429 + `Retry-After`, recovery after refill, absence of raw email/HMAC key in Redis keys, fail-closed 503 during Redis outage and fail-open public discovery with metrics without business correctness loss.

## Backup/restore and rollback evidence

The source database is never overwritten. Backup A -> restore B -> Flyway/invariant/API verification must pass. Then deploy candidate B, smoke, rollback to known-good A against the same schema, smoke, redeploy B and smoke again. If A cannot run after B's migrations, the release is blocked.

## Evidence retention

`build/staging-evidence/<UTC timestamp>/` contains logs/results and is not committed. Record release manifest digest, migration result, P0 summary, security/load result, backup checksum, restore result, rollback timestamps, alert checks, open exceptions and operator sign-off. Never put passwords, OAuth codes, tokens, presigned URLs or private keys in evidence.
