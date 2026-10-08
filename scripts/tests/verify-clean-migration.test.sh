#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
stub_dir="$(mktemp -d)"
trap 'rm -rf -- "$stub_dir"' EXIT

cat >"$stub_dir/psql" <<'STUB'
#!/usr/bin/env bash
query="${*: -1}"
if [[ "$query" == *"where not success"* ]]; then
  echo 0
else
  seq 1 15
fi
STUB
chmod +x "$stub_dir/psql"

PGDATABASE=covie_test PATH="$stub_dir:$PATH" bash "$repo_root/infra/scripts/verify-clean-migration.sh" \
  | grep -F "Clean Flyway sequence 1..15 verified"

if PGDATABASE=covie_test COVIE_EXPECTED_FLYWAY_VERSION=14 PATH="$stub_dir:$PATH" \
  bash "$repo_root/infra/scripts/verify-clean-migration.sh" >/dev/null 2>&1; then
  echo "expected an explicit version 14 check to reject a 15-migration history" >&2
  exit 1
fi
