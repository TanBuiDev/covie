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

### V022 concurrent offer lookup index

V022 creates `ix_location_variant_variant` on `location_variants(variant_id)`
without changing business rows. Its `.sql.conf` sets `executeInTransaction=false`.
The application sets `spring.flyway.postgresql.transactional-lock=false`, retaining
session-level Flyway advisory locking instead of transactional locking, as
required for concurrent index creation. Standalone Flyway invocations must also
set `flyway.postgresql.transactional.lock=false` (CLI/environment equivalent:
`FLYWAY_POSTGRESQL_TRANSACTIONAL_LOCK=false`); they do not read Spring YAML.
See [Flyway PostgreSQL locking](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-postgresql-namespace/flyway-postgresql-transactional-lock-setting)
and [per-script transaction configuration](https://documentation.red-gate.com/flyway/reference/script-configuration).

Do not wrap V022 in an external transaction. Concurrent builds can still wait on
active transactions and consume CPU/I/O/disk; a local disposable migration is
not staging-scale or zero-downtime evidence. Before release, measure build time,
write latency, available disk and long-running transactions on the actual staging
shape. Afterward verify Flyway success and both `pg_index.indisvalid` and
`indisready` for the named index. If creation fails or is interrupted, inspect the
index and migration history before any retry: a nontransactional failure can leave
an invalid index. Do not silently skip an existing index, auto-repair history,
drop an index or remove business data as a recovery shortcut. Cleanup/retry is an
explicit operator recovery decision; application rollback does not undo V022.

## Rollback

```bash
sudo /opt/covie/current/infra/deploy/rollback.sh
```

Rollback validates the runtime environment (including the internal API URL and readable JWT keys) and TLS files before pulling the digests in `previous.env`. It restarts API/Web with those digests, waits for health, runs smoke and swaps release-state files only after success. It does **not** revert Flyway, drop schema objects or restore a database.

If the previous application cannot run against the candidate database schema, the release violates the pilot forward-compatible migration strategy and must not be approved. Redesign the migration/release rather than adding an automatic down migration.

### Catalog video compatibility floor (V023 and later)

Adding nullable columns is not sufficient for old Java enum readers: the local
pre-video API image boots against V025 but returns Seller detail 500 when an
associated asset has purpose `CATALOG_VIDEO`. Its health endpoint still succeeds.
Turning video OFF on that old binary does not repair the enum mapping. Current
video-aware API with video OFF reads the same item successfully, omits video
from public/management media and retains stored associations.

Therefore video-era rollback fails closed before Docker operations unless the
**source** release manifest records the exact rehearsed target `ROLLBACK_API_IMAGE`
and `ROLLBACK_EVIDENCE_SHA256`. A Flyway number alone does not certify compatible
readers. This conservative guard applies from V023 even if no video was published;
it does not query or change production data. Legacy source releases before V023
retain their existing rollback path. Missing source identity also fails closed.
Automatic deploy-error rollback checks the candidate manifest, not the still-old
`current.env`, because the candidate may already have migrated/written data.

Use a two-step expand/activate rollout: first deploy video-aware readers/writers
with all video flags OFF and establish a compatible rollback binary; only later
consider video activation after the actual gates pass. Rehearse the exact source
and target digests against a populated disposable copy, including video records,
Seller/Admin reads and writes, public visibility and preserved data. Then supply
`COVIE_VERIFIED_ROLLBACK_API_IMAGE` and `COVIE_ROLLBACK_EVIDENCE` to manifest
generation. The generator hashes the nonempty evidence file; these fields are an
operator/CI attestation, **not an automatic proof or permission to invent evidence**.
Archive the evidence alongside the immutable manifest. Omitted fields intentionally
leave binary rollback blocked. Do not copy attestations between source releases.

Until that pairing is proven, retain the compatible current binary with video
flags OFF or roll forward to a verified fix. Do not force an old digest, remove
video rows/links, bulk-delete objects or revert migrations to make it boot/read.
Local image experiments are not released-digest/staging rehearsal evidence.

## Data incident

Database corruption is a separate incident path: stop/limit writes, preserve evidence, restore PITR/backup into a **new** database, verify Flyway and business invariants, then switch connectivity only after an explicit incident decision. Never make app rollback silently restore data.
