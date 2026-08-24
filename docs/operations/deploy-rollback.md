# Deploy and rollback

CoVie pilot deploys **immutable image digests** from a generated `release-manifest.env`. A mutable branch/tag is never the production artifact. The database remains forward-only under Flyway; application rollback does not run a down migration.

## Host contract

```text
/var/lib/covie/releases/current.env
/var/lib/covie/releases/previous.env
/etc/covie/prod.env
/etc/covie/secrets/
/etc/covie/tls/
/opt/covie/current/   # checked-out deployment scripts/config
```

`current.env` and `previous.env` contain only image digests/build metadata. Runtime secrets stay outside the repository.

## Release

The GitHub release workflow reruns backend/frontend/contract/security gates, builds API/Web images tagged with the full Git SHA, pushes to GHCR, resolves immutable digests, scans them, emits CycloneDX SBOMs and generates the manifest. Production uses the same digest that was deployed to staging; it is never rebuilt between environments.

## Deploy

```bash
sudo /opt/covie/current/infra/deploy/deploy.sh /tmp/release-manifest.env
```

Preflight requires runtime env validation, TLS files, Docker/Compose, >=20% disk free, an immutable manifest and a verified logical backup no older than 26 hours. The script pulls exact digests, validates Compose and Nginx, saves the previous manifest, starts the stack, waits for health and runs public smoke. Failed smoke attempts application rollback when a previous manifest exists.

Before approval, the operator must additionally verify host clock, DNS, database/Redis/S3 connectivity and monitoring state. The deploy script intentionally does not print secret values.

## Rollback

```bash
sudo /opt/covie/current/infra/deploy/rollback.sh
```

Rollback pulls `previous.env`, restarts API/Web with those digests, waits for health, runs smoke and swaps release-state files only after success. It does **not** revert Flyway, drop schema objects or restore a database.

If the previous application cannot run against the candidate database schema, the release violates the pilot forward-compatible migration strategy and must not be approved. Redesign the migration/release rather than adding an automatic down migration.

## Data incident

Database corruption is a separate incident path: stop/limit writes, preserve evidence, restore PITR/backup into a **new** database, verify Flyway and business invariants, then switch connectivity only after an explicit incident decision. Never make app rollback silently restore data.
