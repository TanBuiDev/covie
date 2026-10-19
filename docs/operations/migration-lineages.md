# Preserved migration lineages

Social Product Card and Seller Billing were verified in isolated databases before
integration, and both had already used V020–V025. A single version-to-script mapping
cannot validate both applied histories. Do not rename applied files, run Flyway repair,
disable validation, change checksums, baseline an existing database or reset data to
resolve this collision.

## Resource mapping

| Existing history | Selected resources | Integration additions |
| --- | --- | --- |
| Fresh or common V001–V019 | `db/migration` | Billing020–034, Social035–040 |
| Billing V020–V034 | `db/migration` | Social035–040 |
| Social V020–V025 | `db/migration-social` and `db/migration/future` | Billing026–040 |

Both chains end with the same application schema at V040. The copied SQL bytes and
existing scripts remain unchanged, including the nontransactional concurrent-index
companion. Common V001–V019 are retained byte-for-byte in both trees.

`MigrationLineageConfiguration` reads the configured datasource's history table in
the configured default schema (otherwise the first configured schema/current schema).
It selects Social only for the exact successful `V020__catalog_social_caption.sql`;
the canonical marker is `V020__seller_billing_foundation.sql`. Failed, unknown or
incomplete histories and read errors stop startup. This read does not replace Flyway's
normal checksum/description validation before applying pending migrations.

## Future migrations and tools

Every new migration starts at V041 and belongs under `db/migration/future`, shared
by both lineages. Canonical `db/migration` is recursive. Do not place future SQL in
only the legacy tree or at the canonical root. A standalone Flyway tool targeting a
Social database must use the two Social locations and retain validation; programmatic
application tools should invoke the same customizer before loading Flyway. A version
number alone cannot identify a database's features or a compatible rollback binary.

## Operator precautions

Before an authorized upgrade, identify the actual host/database/schema, create and
verify a backup, inspect the full history and use the exact candidate artifact on a
restored disposable copy first. Validate business data, both feature areas and the
second startup. Preserve original history rows; the integration does not write them.
Unknown/mixed/checksum-drift histories require investigation, not automatic repair.

The source merge is not permission to connect to the recovered local database or any
shared environment. No existing database is upgraded by the handoff work. Social,
video, money/release/reconciliation and enforcement activation remain separate gates.
