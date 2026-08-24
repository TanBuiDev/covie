# CoVie V1 pilot launch checklist

This checklist is an evidence gate. An unchecked release blocker means **do not launch**.

## Clean verification

- [ ] `cd apps/api && ./mvnw clean verify` exits 0 with zero failures/errors.
- [ ] `./infra/scripts/verify-generated-client.sh` exits 0 and generated contract diff is clean.
- [ ] API client check, Web lint, typecheck, tests and production build all exit 0.
- [ ] Critical security/concurrency suites run explicitly with no quarantine/skip: refresh reuse/concurrency, CSRF negatives, IDOR, staff scope, admin auth/read-only Order, last-stock soak, Order command races, Outbox multi-replica/dedupe, Need timing, Review eligibility, rate-limit atomicity/failure modes and retention concurrency.
- [ ] Plan 09 P0 browser suite passes through normal authentication; production test-support remains disabled.

## Release artifacts and security

- [ ] `./infra/scripts/verify-production-artifacts.sh` passes.
- [ ] `./infra/scripts/verify-nginx-prod.sh` passes.
- [ ] `./infra/scripts/verify-monitoring-config.sh` passes.
- [ ] `bash -n infra/deploy/*.sh infra/backup/*.sh infra/scripts/*.sh` passes.
- [ ] API and Web images are non-root and referenced by immutable SHA-256 digest.
- [ ] Trivy 0.72.0 filesystem + runtime-image HIGH/CRITICAL gate passes.
- [ ] Node production dependency audit passes.
- [ ] API/Web CycloneDX SBOMs are stored with the release evidence.
- [ ] No real `.env`, PEM/private key, registry credential, OAuth secret or backup is tracked/baked into images/logs.
- [ ] Public `/actuator/*` is blocked; API/Web/management/monitoring/PostgreSQL/Redis are not directly internet-exposed.
- [ ] Production cookies have the required Secure/HttpOnly/SameSite behavior.

## Load and correctness

- [ ] Public search objective passes at concurrency 50 with p95 <= 500 ms and errors <1%.
- [ ] Public item detail objective passes at concurrency 50 with p95 <= 300 ms and errors <1%.
- [ ] Authenticated Order list objective passes at concurrency 20 with p95 <= 500 ms and errors <1%.
- [ ] Hikari sustained active/max stays below 80%; PostgreSQL is not connection-saturated.
- [ ] Outbox oldest due returns below 60 seconds after load settles.
- [ ] Last-stock 100-iteration soak shows no oversell/deadlock/duplicate mutation.
- [ ] Order command race soak shows no duplicate events/inventory movements.

## Backup, restore and rollback

- [ ] Latest logical backup is <=26h old and checksum verifies.
- [ ] Encrypted off-host copy exists.
- [ ] Provider PITR/WAL status is documented when supported.
- [ ] Restore rehearsal into a separate database succeeds.
- [ ] Restored database Flyway validation succeeds.
- [ ] Restored Order/OrderItem/OrderEvent/inventory/Need/Review/settings invariants match source evidence.
- [ ] Temporary API against restored DB passes smoke.
- [ ] Candidate -> previous digest -> candidate rollback drill passes against the same schema.
- [ ] Previous application remains compatible with candidate migrations; no down migration is required.

## Monitoring and operator readiness

- [ ] Prometheus scrapes API management metrics and node exporter.
- [ ] Grafana pilot dashboard loads.
- [ ] Alertmanager test reaches the operator channel.
- [ ] API-down alert tested and resolves.
- [ ] Outbox alert tested and resolves.
- [ ] Backup-stale alert tested and resolves.
- [ ] Disk alert expression validated.
- [ ] Release Operator assigned.
- [ ] Incident Lead assigned.
- [ ] Database Operator assigned.

Pilot internal objectives (not a public SLA): monthly API availability objective 99.5%; core API read p95 <1s under expected pilot load; Outbox oldest due <60s; backup freshness <26h; logical-backup RPO <=24h; restore RTO <=4h.

## Release blockers

- [ ] Tracked inventory cannot oversell.
- [ ] Same Idempotency-Key cannot duplicate an Order.
- [ ] Refresh reuse revokes the session family.
- [ ] Buyer cannot read another buyer's private Order.
- [ ] STAFF cannot exceed MerchantLocation scope.
- [ ] No direct PATCH Order status API exists.
- [ ] Need does not fulfill on PENDING Order.
- [ ] Review cannot be created before COMPLETED.
- [ ] Outbox write is atomic with business commit.
- [ ] Outbox retry cannot duplicate projection/notification.
- [ ] Production auth cookies are secure.
- [ ] E2E/test-support is disabled in production.
- [ ] Clean DB migration succeeds.
- [ ] Production startup safety passes.
- [ ] Redis rate-limit outage semantics are rehearsed.
- [ ] `/actuator` is not public.
- [ ] Secrets are absent from source/images/logs.
- [ ] Backup has been restore-tested.
- [ ] Staging rollback drill passes.
- [ ] HIGH/CRITICAL runtime vulnerability exceptions are zero or explicitly approved, dated and unexpired.

## Migration handoff

Normal Plan 10 case:

```text
highest migration = V013
Plan 10 migration = none
next available migration = V014
```

Do not consume V014 for production hardening alone. If a performance gate proves a schema/index change is required, review it separately, capture before/after evidence and update the release manifest/migration handoff.

## Final approval

- [ ] `infra/scripts/run-staging-rehearsal.sh` produced a green dated evidence bundle.
- [ ] All release blockers above are checked.
- [ ] The exact staging-approved API/Web digests are present in the production release manifest.
- [ ] Protected production environment approval is granted.
