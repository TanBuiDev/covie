#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

required=(
  apps/api/Dockerfile
  apps/web/Dockerfile
  apps/api/src/main/resources/application-prod.yml
  infra/compose/compose.prod.yml
  infra/docker/nginx.prod.conf
  infra/docker/security-headers.conf
  infra/deploy/prod.env.example
  infra/deploy/verify-runtime-env.sh
  infra/deploy/deploy.sh
  infra/deploy/smoke.sh
  infra/deploy/rollback.sh
  infra/backup/postgres-backup.sh
  infra/backup/postgres-restore.sh
  infra/backup/verify-dump.sh
  infra/scripts/security-scan.sh
  infra/scripts/generate-sbom.sh
  infra/scripts/create-release-manifest.sh
  .github/workflows/release.yml
)
for file in "${required[@]}"; do
  [[ -f "$file" ]] || { echo "Missing production artifact: $file" >&2; exit 1; }
done

for script in infra/deploy/*.sh infra/backup/*.sh infra/scripts/*.sh; do
  bash -n "$script"
done
node infra/perf/run-load.mjs --help >/dev/null

highest="$(find apps/api/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' -exec basename {} \; \
  | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1)"
[[ -n "$highest" && "$((10#$highest))" -ge 13 ]] || { echo "Plan 10 prerequisite V013 is missing" >&2; exit 1; }

# Production exposure and safety source assertions.
grep -q 'enabled: false' apps/api/src/main/resources/application-prod.yml
grep -q 'rate-limit:' apps/api/src/main/resources/application-prod.yml
grep -q 'enabled: true' apps/api/src/main/resources/application-prod.yml
grep -q 'proxy_buffering off' infra/docker/nginx.prod.conf
grep -q 'Strict-Transport-Security' infra/docker/security-headers.conf
! grep -Eq '(8080|3000|9091):' infra/compose/compose.prod.yml

grep -q 'USER 10001:10001' apps/api/Dockerfile
grep -q 'USER 10001:10001' apps/web/Dockerfile

git ls-files | grep -E '(^|/)(\.env|.*\.pem|.*\.key)$' > /tmp/covie-tracked-sensitive.$$ || true
if [[ -s /tmp/covie-tracked-sensitive.$$ ]]; then
  cat /tmp/covie-tracked-sensitive.$$ >&2
  rm -f /tmp/covie-tracked-sensitive.$$
  echo "Tracked runtime secret-like files detected" >&2
  exit 1
fi
rm -f /tmp/covie-tracked-sensitive.$$

echo "Production artifact validator passed (highest Flyway V$(printf '%03d' "$((10#$highest))"))"
