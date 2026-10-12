#!/usr/bin/env bash
set -euo pipefail

manifest="${1:-}"
[[ -f "$manifest" ]] || { echo "usage: $0 <release-manifest.env>" >&2; exit 2; }

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
compose="$repo_root/infra/compose/compose.prod.yml"
state_dir="${COVIE_RELEASE_STATE_DIR:-/var/lib/covie/releases}"
env_file="${COVIE_ENV_FILE:-/etc/covie/prod.env}"
jwt_dir="${COVIE_JWT_SECRET_DIR:-/etc/covie/secrets}"
tls_dir="${COVIE_TLS_CERT_DIR:-/etc/covie/tls}"
backup_dir="${COVIE_BACKUP_DIR:-/var/lib/covie/backups}"
public_url="${COVIE_PUBLIC_URL:-https://covie.vn}"

value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$manifest" | tail -1 || true)"
  [[ -n "$line" ]] || { echo "Missing manifest key: $key" >&2; exit 2; }
  printf '%s' "${line#*=}"
}

api_image="$(value API_IMAGE)"
web_image="$(value WEB_IMAGE)"
git_sha="$(value GIT_SHA)"
highest="$(value HIGHEST_FLYWAY)"
[[ "$api_image" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "API_IMAGE must be immutable" >&2; exit 2; }
[[ "$web_image" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "WEB_IMAGE must be immutable" >&2; exit 2; }
[[ "$git_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid GIT_SHA" >&2; exit 2; }
[[ "$highest" =~ ^V[0-9]{3,}$ ]] || { echo "Invalid HIGHEST_FLYWAY" >&2; exit 2; }

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }
docker compose version >/dev/null
COVIE_JWT_SECRET_DIR="$jwt_dir" "$repo_root/infra/deploy/verify-runtime-env.sh" "$env_file"
"$repo_root/infra/scripts/verify-nginx-prod.sh"
[[ -r "$tls_dir/fullchain.pem" && -r "$tls_dir/privkey.pem" ]] || { echo "TLS certificate/key missing" >&2; exit 1; }

free_pct="$(df -Pk "$repo_root" | awk 'NR==2 {gsub(/%/,"",$5); print 100-$5}')"
(( free_pct >= 20 )) || { echo "Deployment blocked: less than 20% disk free" >&2; exit 1; }

latest_backup="$(find "$backup_dir" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 || true)"
[[ -n "$latest_backup" ]] || { echo "Deployment blocked: no verified logical backup found" >&2; exit 1; }
backup_epoch="${latest_backup%% *}"; backup_epoch="${backup_epoch%.*}"
age="$(( $(date +%s) - backup_epoch ))"
(( age <= 93600 )) || { echo "Deployment blocked: latest backup is older than 26h" >&2; exit 1; }
backup_file="${latest_backup#* }"
bash "$repo_root/infra/backup/verify-dump.sh" "$backup_file"

mkdir -p "$state_dir"
current="$state_dir/current.env"
previous="$state_dir/previous.env"
if [[ -f "$current" ]]; then cp -f "$current" "$previous"; fi

export COVIE_API_IMAGE="$api_image" COVIE_WEB_IMAGE="$web_image"
export COVIE_ENV_FILE="$env_file" COVIE_JWT_SECRET_DIR="$jwt_dir" COVIE_TLS_CERT_DIR="$tls_dir"

docker pull "$COVIE_API_IMAGE"
docker pull "$COVIE_WEB_IMAGE"
docker compose -f "$compose" config >/dev/null

rollback_on_error() {
  local status=$?
  if (( status != 0 )) && [[ -f "$previous" ]] && [[ "${COVIE_ROLLBACK_IN_PROGRESS:-false}" != "true" ]]; then
    echo "Deployment failed; attempting application rollback" >&2
    COVIE_ROLLBACK_IN_PROGRESS=true "$repo_root/infra/deploy/rollback.sh" || true
  fi
  exit "$status"
}
trap rollback_on_error ERR

# Bring the private application services up first so Nginx's upstream DNS names exist.
# This also lets the API perform forward-only Flyway migration before public ingress is changed.
docker compose -f "$compose" up -d api web

# Validate the exact mounted Nginx configuration on the Compose network before starting/reloading ingress.
docker compose -f "$compose" run --rm --no-deps nginx nginx -t >/dev/null

docker compose -f "$compose" up -d nginx --remove-orphans

for _ in $(seq 1 60); do
  if curl --fail --silent --show-error --max-time 5 "$public_url/api/v1/health" >/dev/null 2>&1; then break; fi
  sleep 2
done
curl --fail --silent --show-error --max-time 10 "$public_url/api/v1/health" >/dev/null
curl --fail --silent --show-error --max-time 10 "$public_url/" >/dev/null
"$repo_root/infra/deploy/smoke.sh" "$public_url"

cp -f "$manifest" "$current.tmp"
chmod 0644 "$current.tmp"
mv "$current.tmp" "$current"
trap - ERR
printf 'Deployed %s\nAPI %s\nWEB %s\n' "$git_sha" "$api_image" "$web_image"
