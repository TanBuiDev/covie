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

The clean database must migrate successfully through V014 (or an explicitly reviewed later migration). `verify-clean-migration.sh` requires a complete successful 1..14 sequence by default. V014 adds the nullable, non-negative measured distance for service fulfillment reads. Enable the committed Grand Park reference bootstrap once, verify idempotency, then disable it. Bootstrap the first staging Admin only after normal Google + local email/password onboarding and remove the bootstrap email immediately after the grant.

The repository contains guest Playwright coverage, but it is not evidence for a normal authenticated staging journey. `apps/web/e2e/staging-p0.spec.ts` is the canonical Buyer/Seller/Admin staging flow and authenticates through CSRF plus `/api/v1/auth/login`; it never calls test-support login. `COVIE_STAGING_P0_CMD` may override the command hook, but the rehearsal must not substitute a fake browser test or guest-only suite.

The disposable staging data must give the Buyer at least one saved address and give the Seller's first merchant one public ACTIVE Product plus one public ACTIVE Service. Their selected offers must be available at an ACTIVE location with Product delivery coverage and enough stock for the Product, Service and Need-sourced orders. The Need discovery location must cover the Buyer's saved address.

## Full gate

The release workflow now runs `infra/deploy/rehearse-release.sh` after staging deployment, before its production dependency can succeed. The wrapper reads the operator-managed `/etc/covie/staging-rehearsal.env` shell configuration, invokes the committed rehearsal and checks that `current.env` matches the candidate manifest both before and after the rollback drill. Keep that configuration root-owned, readable only by the operator/root, outside the repository; it must contain the staging-only variables, confirmation and hooks below. Missing configuration or failed rehearsal blocks promotion. Manifest comparison checks release-state consistency; runtime health and business checks come from the rehearsal itself.

Set staging-only connection variables plus the following command hooks:

```text
COVIE_STAGING_P0_CMD # optional override; defaults to the canonical Playwright staging spec
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

The canonical command is:

```bash
pnpm --dir apps/web test:e2e:staging
```

It requires `STAGING_BUYER_EMAIL`, `STAGING_BUYER_PASSWORD`, `STAGING_SELLER_EMAIL`, `STAGING_SELLER_PASSWORD`, `STAGING_ADMIN_EMAIL`, and `STAGING_ADMIN_PASSWORD`. These values stay in staging CI/environment secrets and are never written to evidence.

## Rate-limit evidence

Controlled staging tests must show auth/login 429 + `Retry-After`, recovery after refill, absence of raw email/HMAC key in Redis keys, fail-closed 503 during Redis outage and fail-open public discovery with metrics without business correctness loss.

## Backup/restore and rollback evidence

The source database is never overwritten. Backup A -> restore B -> Flyway/invariant/API verification must pass. Then deploy candidate B, smoke, rollback to known-good A against the same schema, smoke, redeploy B and smoke again. If A cannot run after B's migrations, the release is blocked.

## Evidence retention

`build/staging-evidence/<UTC timestamp>/` contains logs/results and is not committed. Record release manifest digest, migration result, P0 summary, security/load result, backup checksum, restore result, rollback timestamps, alert checks, open exceptions and operator sign-off. Never put passwords, OAuth codes, tokens, presigned URLs or private keys in evidence.
