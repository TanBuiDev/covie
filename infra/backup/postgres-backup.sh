#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"

backup_dir="${COVIE_BACKUP_DIR:-/var/lib/covie/backups}"
textfile_dir="${COVIE_NODE_EXPORTER_TEXTFILE_DIR:-}"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

ts="$(date -u +%Y%m%dT%H%M%SZ)"
base="covie-${PGDATABASE}-${ts}"
tmp="$(mktemp "$backup_dir/.${base}.XXXXXX.dump")"
final="$backup_dir/${base}.dump"
meta="$final.meta"
checksum="$final.sha256"
cleanup() { rm -f "$tmp" "$meta.tmp" "$checksum.tmp"; }
trap cleanup EXIT

pg_dump --format=custom --compress=6 --no-owner --no-acl --file "$tmp" "$PGDATABASE"
pg_restore --list "$tmp" >/dev/null
sha="$(sha256sum "$tmp" | awk '{print $1}')"
highest_flyway="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select coalesce(max(version),'none') from flyway_schema_history where success")"

chmod 600 "$tmp"
mv "$tmp" "$final"
printf '%s  %s\n' "$sha" "$(basename "$final")" > "$checksum.tmp"
chmod 600 "$checksum.tmp"
mv "$checksum.tmp" "$checksum"
cat > "$meta.tmp" <<EOF
database=$PGDATABASE
created_utc=$ts
sha256=$sha
highest_flyway=$highest_flyway
EOF
chmod 600 "$meta.tmp"
mv "$meta.tmp" "$meta"

if [[ -n "$textfile_dir" ]]; then
  mkdir -p "$textfile_dir"
  prom_tmp="$(mktemp "$textfile_dir/.covie_backup.XXXXXX.prom")"
  printf 'covie_postgres_backup_timestamp_seconds %s\n' "$(date -u +%s)" > "$prom_tmp"
  chmod 644 "$prom_tmp"
  mv "$prom_tmp" "$textfile_dir/covie_backup.prom"
fi

printf 'Backup created: %s\n' "$final"
printf 'SHA256: %s\n' "$sha"
printf 'Highest Flyway: %s\n' "$highest_flyway"
