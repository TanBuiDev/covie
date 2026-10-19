#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
fixture="$(mktemp -d "$root/.local/staging-rehearsal-snapshot-gate.XXXXXX")"
cleanup() {
  case "$(cd "$fixture" && pwd -P)" in
    "$root"/.local/staging-rehearsal-snapshot-gate.*) rm -rf -- "$fixture" ;;
    *) echo 'Refusing unexpected fixture cleanup path' >&2 ;;
  esac
}
trap cleanup EXIT
mkdir -p "$fixture/infra/scripts" "$fixture/infra/deploy" "$fixture/infra/backup" "$fixture/infra/perf" "$fixture/bin"
cp "$root/infra/scripts/run-staging-rehearsal.sh" "$root/infra/scripts/verify-restored-invariants.sh" "$fixture/infra/scripts/"
for script in infra/scripts/verify-clean-migration.sh infra/scripts/security-scan.sh infra/deploy/smoke.sh; do
  printf '#!/usr/bin/env bash\nset -euo pipefail\nexit 0\n' > "$fixture/$script"
done
cat > "$fixture/bin/psql" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
query=''
while (($#)); do
  if [[ "$1" == '-c' ]]; then query="$2"; break; fi
  shift
done
if [[ "$query" == *to_regclass* ]]; then
  echo t
elif [[ "$query" == *flyway_schema_history* ]]; then
  echo 022
elif [[ "$query" == *'select count(*) from'* ]]; then
  count=1
  if [[ "$query" == *'"orders"'* || "$query" == *'"reviews"'* ]]; then
    if [[ "$PGDATABASE" == snapshot_source ]]; then
      count="$(cat "$FIXTURE/source-count")"
    else
      count="$(cat "$FIXTURE/restored-count")"
    fi
  fi
  echo "$count"
else
  echo 'Unexpected invariant SQL' >&2
  exit 3
fi
STUB
printf '#!/usr/bin/env bash\nprintf "{}\\n"\n' > "$fixture/bin/node"
cat > "$fixture/p0.sh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '2\n' > "$FIXTURE/source-count"
STUB
cat > "$fixture/infra/backup/postgres-backup.sh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$COVIE_BACKUP_DIR"
cp "$FIXTURE/source-count" "$COVIE_BACKUP_DIR/fixture.dump"
STUB
cat > "$fixture/infra/backup/postgres-restore.sh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
cp "$1" "$FIXTURE/restored-count"
if [[ "$SIMULATE_RESTORE_LOSS" == 1 ]]; then
  printf '1\n' > "$FIXTURE/restored-count"
fi
STUB
chmod +x "$fixture/bin/psql" "$fixture/bin/node" "$fixture/infra/scripts/"*.sh "$fixture/infra/deploy/"*.sh "$fixture/infra/backup/"*.sh

run_rehearsal() {
  local loss="$1"
  printf '1\n' > "$fixture/source-count"
  env -i PATH="$fixture/bin:$PATH" HOME="$fixture" FIXTURE="$fixture" \
    PGHOST=fixture PGPORT=1 PGDATABASE=snapshot_source PGUSER=fixture PGPASSWORD=disposable-stub \
    COVIE_REHEARSAL_CONFIRM=STAGING_ONLY COVIE_STAGING_BASE_URL=https://staging.example.invalid \
    COVIE_RESTORE_DATABASE=snapshot_restore COVIE_RESTORE_PGHOST=fixture COVIE_RESTORE_PGPORT=1 \
    COVIE_RESTORE_PGUSER=fixture COVIE_RESTORE_PGPASSWORD=disposable-stub \
    COVIE_STAGING_P0_CMD="bash \"$fixture/p0.sh\"" COVIE_RATE_LIMIT_REHEARSAL_CMD=true \
    COVIE_RESTORED_API_SMOKE_CMD=true COVIE_ROLLBACK_DRILL_CMD=true COVIE_ALERT_REHEARSAL_CMD=true \
    SIMULATE_RESTORE_LOSS="$loss" COVIE_REHEARSAL_EVIDENCE_DIR="$fixture/evidence-$loss" \
    bash "$fixture/infra/scripts/run-staging-rehearsal.sh" > "$fixture/run-$loss.log" 2>&1
}

if ! run_rehearsal 0; then
  cat "$fixture/run-0.log" >&2
  echo 'FAIL: exact post-P0 backup/restore rejected against obsolete pre-P0 snapshot' >&2
  exit 1
fi
grep -qx 'orders=2' "$fixture/evidence-0/db-invariants.env"
grep -qx 'reviews=2' "$fixture/evidence-0/db-invariants.env"
grep -qx 'result=PASS' "$fixture/evidence-0/SUMMARY.txt"
if run_rehearsal 1; then
  echo 'FAIL: restore losing post-P0 orders/reviews was accepted' >&2
  exit 1
fi
grep -q 'Restored database invariant counts differ' "$fixture/run-1.log"
[[ ! -f "$fixture/evidence-1/SUMMARY.txt" ]]
echo 'PASS: snapshot matches post-P0 backup; restored loss still rejects before PASS'
