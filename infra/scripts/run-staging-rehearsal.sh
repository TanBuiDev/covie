#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

[[ "${COVIE_REHEARSAL_CONFIRM:-}" == "STAGING_ONLY" ]] || {
  echo "Set COVIE_REHEARSAL_CONFIRM=STAGING_ONLY after confirming every target is disposable/staging" >&2; exit 2;
}
: "${COVIE_STAGING_BASE_URL:?COVIE_STAGING_BASE_URL is required}"
: "${PGDATABASE:?Source staging PGDATABASE is required}"
: "${COVIE_RESTORE_DATABASE:?COVIE_RESTORE_DATABASE must name a separate empty restore DB}"
: "${COVIE_RATE_LIMIT_REHEARSAL_CMD:?Set COVIE_RATE_LIMIT_REHEARSAL_CMD for controlled Redis/rate-limit failure checks}"
: "${COVIE_RESTORED_API_SMOKE_CMD:?Set COVIE_RESTORED_API_SMOKE_CMD for temporary API validation against restored DB}"
: "${COVIE_ROLLBACK_DRILL_CMD:?Set COVIE_ROLLBACK_DRILL_CMD for B -> A -> B digest drill}"
: "${COVIE_ALERT_REHEARSAL_CMD:?Set COVIE_ALERT_REHEARSAL_CMD for controlled alert trigger/resolve checks}"

[[ "$COVIE_STAGING_BASE_URL" == https://* ]] || { echo "Staging target must be HTTPS" >&2; exit 2; }
case "$COVIE_STAGING_BASE_URL" in
  https://covie.vn|https://covie.vn/*) echo "Refusing rehearsal against production origin" >&2; exit 1 ;;
esac
[[ "$COVIE_RESTORE_DATABASE" != "$PGDATABASE" ]] || { echo "Restore DB must differ from source DB" >&2; exit 1; }
[[ "$COVIE_RESTORE_DATABASE" != "${COVIE_PRODUCTION_DB_NAME:-covie}" ]] || { echo "Refusing production-named restore DB" >&2; exit 1; }

: "${COVIE_STAGING_P0_CMD:=pnpm --dir \"$root/apps/web\" test:e2e:staging}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
evidence="${COVIE_REHEARSAL_EVIDENCE_DIR:-$root/build/staging-evidence/$stamp}"
mkdir -p "$evidence"
exec > >(tee -a "$evidence/rehearsal.log") 2>&1

echo "== CoVie staging rehearsal $stamp =="
echo "Target: $COVIE_STAGING_BASE_URL"
echo "Source DB: $PGDATABASE; restore DB: $COVIE_RESTORE_DATABASE"

"$root/infra/scripts/verify-clean-migration.sh"
"$root/infra/deploy/smoke.sh" "$COVIE_STAGING_BASE_URL"
"$root/infra/scripts/security-scan.sh"

node "$root/infra/perf/run-load.mjs" \
  --base-url "$COVIE_STAGING_BASE_URL" \
  --scenario public-search \
  --concurrency "${COVIE_REHEARSAL_LOAD_CONCURRENCY:-50}" \
  --duration "${COVIE_REHEARSAL_LOAD_DURATION:-120s}" \
  | tee "$evidence/load-public-search.json"

bash -lc "$COVIE_STAGING_P0_CMD" | tee "$evidence/p0.log"
bash -lc "$COVIE_RATE_LIMIT_REHEARSAL_CMD" | tee "$evidence/rate-limit.log"

source_db="$PGDATABASE"
source_host="${PGHOST:-}"
source_port="${PGPORT:-}"
source_user="${PGUSER:-}"
source_password="${PGPASSWORD:-}"
export COVIE_BACKUP_DIR="$evidence/backup"
"$root/infra/scripts/verify-restored-invariants.sh" --snapshot "$evidence/db-invariants.env"
"$root/infra/backup/postgres-backup.sh"
backup="$(find "$COVIE_BACKUP_DIR" -type f -name '*.dump' -print | sort | tail -1)"
[[ -n "$backup" ]] || { echo "Backup artifact missing" >&2; exit 1; }

: "${COVIE_RESTORE_PGHOST:?COVIE_RESTORE_PGHOST is required}"
: "${COVIE_RESTORE_PGPORT:?COVIE_RESTORE_PGPORT is required}"
: "${COVIE_RESTORE_PGUSER:?COVIE_RESTORE_PGUSER is required}"
: "${COVIE_RESTORE_PGPASSWORD:?COVIE_RESTORE_PGPASSWORD is required}"

export PGHOST="$COVIE_RESTORE_PGHOST" PGPORT="$COVIE_RESTORE_PGPORT" PGUSER="$COVIE_RESTORE_PGUSER" PGPASSWORD="$COVIE_RESTORE_PGPASSWORD" PGDATABASE="$COVIE_RESTORE_DATABASE"
export COVIE_RESTORE_CONFIRM=RESTORE
"$root/infra/backup/postgres-restore.sh" "$backup"
"$root/infra/scripts/verify-clean-migration.sh"
"$root/infra/scripts/verify-restored-invariants.sh" --verify "$evidence/db-invariants.env"
bash -lc "$COVIE_RESTORED_API_SMOKE_CMD" | tee "$evidence/restore-api-smoke.log"

export PGHOST="$source_host" PGPORT="$source_port" PGUSER="$source_user" PGPASSWORD="$source_password" PGDATABASE="$source_db"
bash -lc "$COVIE_ROLLBACK_DRILL_CMD" | tee "$evidence/rollback-drill.log"
bash -lc "$COVIE_ALERT_REHEARSAL_CMD" | tee "$evidence/alerts.log"

cat > "$evidence/SUMMARY.txt" <<EOF
rehearsal_utc=$stamp
staging_url=$COVIE_STAGING_BASE_URL
source_db=$source_db
restore_db=$COVIE_RESTORE_DATABASE
backup=$(basename "$backup")
result=PASS
EOF

echo "Staging rehearsal PASS. Evidence: $evidence"
