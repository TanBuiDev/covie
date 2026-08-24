# PostgreSQL backup and restore

Pilot objectives: daily portable logical backup; backup-freshness alert after 26 hours; logical-backup RPO objective <=24h; preferred provider PITR/WAL RPO <=15m when available; restore RTO objective <=4h. These are operator objectives, not a customer SLA.

`infra/backup/postgres-backup.sh` requires standard `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`. It creates a custom-format compressed dump, validates it with `pg_restore --list`, records SHA-256 and highest successful Flyway version, applies mode 0600, and atomically updates the node_exporter textfile metric when `COVIE_NODE_EXPORTER_TEXTFILE_DIR` is set. It never prints the database password.

A same-host dump is only staging. Copy every successful dump, checksum and metadata file to a private encrypted off-host destination with retention and operator-only restore permission. Provider PITR/WAL should be enabled when available and rehearsed into a new instance; logical dumps remain the independent portable recovery path.

Restore only into a new/empty database. Set `COVIE_RESTORE_CONFIRM=RESTORE`; production-named targets are rejected unless an explicit change window uses `COVIE_ALLOW_PRODUCTION_RESTORE=true`. `postgres-restore.sh` verifies checksum and never modifies the source dump. Then run `verify-backup.sh` and start a temporary API against the restored DB with Flyway validation plus critical read smoke. Never use `flyway repair` to hide checksum drift.

A release rehearsal must prove DB A remains untouched: seed known orders/reviews, backup A, create empty DB B, restore B, validate Flyway and invariant counts, run a temporary API smoke, then destroy B only after evidence is recorded.
