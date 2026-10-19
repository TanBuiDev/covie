# Database environments

Reconciled on 2026-10-15 from the archived database-guidance branch against the
current application, deployment and restore scripts.

## Environment separation

Use the same application and forward-only Flyway migration chain across local,
demo, staging and production. Select the database through protected runtime
configuration, not source changes or promotion of demo database contents.

| Environment | Purpose | Data handling |
| --- | --- | --- |
| Local | Development and disposable verification | Synthetic fixtures only on an explicitly identified disposable target |
| Demo | Synthetic demonstrations | Separate from production; do not assume the existing recovered local database is disposable |
| Staging | Rehearsal of the exact candidate release | Isolated services and credentials; follow the staging rehearsal runbook |
| Production | Actual user and business data | No demo seed/reset; use the deployment and backup/restore procedures |

Database names alone do not establish environment identity. Check the connection
host, database, credentials, owner and intended operation before writes.

## Current configuration and scripts

- API JDBC connection: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in
  `apps/api/src/main/resources/application.yml`. Local defaults are not a production
  configuration. `infra/deploy/verify-runtime-env.sh` checks production settings.
- PostgreSQL backup/restore tools use `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`
  and `PGPASSWORD`, independently of the API JDBC variables. Point both explicitly
  at their intended environment; never assume they identify the same target.
- `infra/backup/verify-dump.sh` checks the SHA-256 sidecar and `pg_restore --list`.
- `infra/backup/postgres-restore.sh` requires `COVIE_RESTORE_CONFIRM=RESTORE`, a
  checksum-valid archive and an empty target. It rejects the configured production
  database name unless its explicit production override is set. These are script
  guards, not a substitute for checking the actual target identity.
- `infra/scripts/run-staging-rehearsal.sh` requires
  `COVIE_REHEARSAL_CONFIRM=STAGING_ONLY`, an HTTPS staging URL, separate source/restore
  databases and the configured business/rollback/alert hooks. The production
  origin and production-named restore target are rejected.
- Social acceptance uses `infra/scripts/run-social-phase4-staging.sh` and its own
  `COVIE_SOCIAL_PHASE4_CONFIRM=STAGING_ONLY` contract. Do not interchange these
  confirmations with the restore confirmation.

Keep applied migrations unchanged. Current V014 measures service distance; the
historical branch's merchant-kind V014 was not integrated and must not replace it.

## Related procedures

- [Demo data](demo-data.md)
- [Staging rehearsal](staging-rehearsal.md)
- [Backup and restore](backup-restore.md)
- [Deployment and rollback](deploy-rollback.md)
- [Pilot launch checklist](pilot-launch-checklist.md)
- [Social backend evidence](social-backend-review-2026-10-15.md)

No staging is currently available, as confirmed by the user. This document records
policy and inspected script contracts; it does not claim a completed rehearsal.
