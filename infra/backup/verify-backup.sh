#!/usr/bin/env bash
set -euo pipefail
: "${PGDATABASE:?PGDATABASE is required}"

psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select version, checksum from flyway_schema_history where success order by installed_rank" >/dev/null

for table in orders order_events inventory_movements reviews; do
  exists="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select to_regclass('public.$table') is not null")"
  if [[ "$exists" == "t" ]]; then
    count="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select count(*) from \"$table\"")"
    printf '%s=%s\n' "$table" "$count"
  fi
done

if [[ -n "${COVIE_RESTORE_API_VERIFY_CMD:-}" ]]; then
  bash -lc "$COVIE_RESTORE_API_VERIFY_CMD"
else
  echo "API/Flyway runtime validation not executed; set COVIE_RESTORE_API_VERIFY_CMD for the disposable restore target."
fi
