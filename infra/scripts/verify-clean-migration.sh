#!/usr/bin/env bash
set -euo pipefail

: "${PGDATABASE:?PGDATABASE is required}"
expected="${COVIE_EXPECTED_FLYWAY_VERSION:-13}"
[[ "$expected" =~ ^[0-9]+$ ]] || { echo "COVIE_EXPECTED_FLYWAY_VERSION must be numeric" >&2; exit 2; }

failed="$(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c "select count(*) from flyway_schema_history where not success")"
[[ "$failed" == "0" ]] || { echo "Flyway history contains failed migrations" >&2; exit 1; }

mapfile -t versions < <(psql -XAtq -v ON_ERROR_STOP=1 -d "$PGDATABASE" -c \
  "select nullif(regexp_replace(version, '[^0-9].*$', ''), '')::int from flyway_schema_history where success and version is not null order by installed_rank")

[[ "${#versions[@]}" -eq "$expected" ]] || {
  echo "Expected $expected successful versioned migrations, found ${#versions[@]}" >&2; exit 1;
}
for ((i=1; i<=expected; i++)); do
  [[ "${versions[$((i-1))]}" == "$i" ]] || {
    echo "Flyway sequence mismatch at position $i: ${versions[$((i-1))]:-missing}" >&2; exit 1;
  }
done

echo "Clean Flyway sequence 1..$expected verified"
