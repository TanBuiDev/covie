#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
baseline="${2:-build/staging-evidence/db-invariants.env}"
[[ "$mode" == "--snapshot" || "$mode" == "--verify" ]] || {
  echo "usage: $0 --snapshot|--verify [baseline-file]" >&2; exit 2;
}
: "${PGDATABASE:?PGDATABASE is required}"

mkdir -p "$(dirname "$baseline")"
tables=(orders order_items order_events inventory_movements need_requests reviews system_setting_history)

gather() {
  for table in "${tables[@]}"; do
    exists="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -v table="$table" -c "select to_regclass('public.' || :'table') is not null")"
    [[ "$exists" == "t" ]] || { echo "Required invariant table missing: $table" >&2; return 1; }
    count="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select count(*) from \"$table\"")"
    printf '%s=%s\n' "$table" "$count"
  done
  flyway="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select coalesce(max(version),'') from flyway_schema_history where success")"
  printf 'flyway=%s\n' "$flyway"
}

if [[ "$mode" == "--snapshot" ]]; then
  gather > "$baseline.tmp"
  mv "$baseline.tmp" "$baseline"
  echo "Invariant baseline written: $baseline"
  exit 0
fi

[[ -f "$baseline" ]] || { echo "Missing invariant baseline: $baseline" >&2; exit 2; }
actual="$(mktemp)"
trap 'rm -f "$actual"' EXIT
gather > "$actual"
if ! diff -u "$baseline" "$actual"; then
  echo "Restored database invariant counts differ from source snapshot" >&2
  exit 1
fi

echo "Restored database invariant counts match source snapshot"
