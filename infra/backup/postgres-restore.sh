#!/usr/bin/env bash
set -euo pipefail

backup="${1:-}"
[[ -f "$backup" ]] || { echo "usage: $0 <backup.dump>" >&2; exit 2; }
: "${COVIE_RESTORE_CONFIRM:?Set COVIE_RESTORE_CONFIRM=RESTORE}"
[[ "$COVIE_RESTORE_CONFIRM" == "RESTORE" ]] || { echo "Restore confirmation rejected" >&2; exit 1; }
: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"

prod_db="${COVIE_PRODUCTION_DB_NAME:-covie}"
if [[ "$PGDATABASE" == "$prod_db" && "${COVIE_ALLOW_PRODUCTION_RESTORE:-false}" != "true" ]]; then
  echo "Refusing restore into production-named database; restore to a new database first." >&2
  exit 1
fi

checksum_file="$backup.sha256"
[[ -f "$checksum_file" ]] || { echo "Missing checksum file: $checksum_file" >&2; exit 1; }
expected="$(awk 'NR==1 {print $1}' "$checksum_file")"
actual="$(sha256sum "$backup" | awk '{print $1}')"
[[ -n "$expected" && "$expected" == "$actual" ]] || { echo "Backup checksum mismatch" >&2; exit 1; }
pg_restore --list "$backup" >/dev/null

object_count="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select count(*) from pg_catalog.pg_class where relnamespace not in (select oid from pg_namespace where nspname in ('pg_catalog','information_schema'))")"
[[ "$object_count" == "0" ]] || { echo "Target database is not empty" >&2; exit 1; }

pg_restore --exit-on-error --no-owner --no-acl --dbname "$PGDATABASE" "$backup"
echo "Restore completed into target database: $PGDATABASE"
