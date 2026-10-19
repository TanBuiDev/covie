#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
compose="$repo_root/infra/compose/compose.prod.yml"
state_dir="${COVIE_RELEASE_STATE_DIR:-/var/lib/covie/releases}"
env_file="${COVIE_ENV_FILE:-/etc/covie/prod.env}"
jwt_dir="${COVIE_JWT_SECRET_DIR:-/etc/covie/secrets}"
tls_dir="${COVIE_TLS_CERT_DIR:-/etc/covie/tls}"
public_url="${COVIE_PUBLIC_URL:-https://covie.vn}"
current="$state_dir/current.env"
previous="$state_dir/previous.env"

[[ -f "$previous" ]] || { echo "No previous immutable release manifest to roll back to" >&2; exit 1; }

target="$previous"
value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$target" | tail -1 || true)"
  [[ -n "$line" ]] || { echo "Previous manifest missing $key" >&2; exit 2; }
  printf '%s' "${line#*=}"
}

api_image="$(value API_IMAGE)"
web_image="$(value WEB_IMAGE)"
[[ "$api_image" =~ @sha256:[0-9a-f]{64}$ && "$web_image" =~ @sha256:[0-9a-f]{64}$ ]] || {
  echo "Previous release is not digest-pinned" >&2; exit 1;
}

source_manifest="${COVIE_ROLLBACK_FROM_MANIFEST:-$current}"
[[ -r "$source_manifest" ]] || { echo "Rollback source manifest is required" >&2; exit 1; }
source_highest="$(sed -n 's/^HIGHEST_FLYWAY=//p' "$source_manifest")"
[[ "$source_highest" =~ ^V[0-9]{3,6}$ ]] || { echo "Invalid rollback source migration identity" >&2; exit 1; }
if (( 10#${source_highest#V} >= 23 )); then
  compatible_api="$(sed -n 's/^ROLLBACK_API_IMAGE=//p' "$source_manifest")"
  evidence_sha="$(sed -n 's/^ROLLBACK_EVIDENCE_SHA256=//p' "$source_manifest")"
  [[ "$compatible_api" == "$api_image" && "$evidence_sha" =~ ^[0-9a-f]{64}$ ]] || {
    echo "Rollback blocked: video-era source requires a rehearsed target API digest and evidence hash. Keep the compatible API with video flags OFF; do not delete media or revert migrations." >&2
    exit 1
  }
fi

export COVIE_API_IMAGE="$api_image" COVIE_WEB_IMAGE="$web_image"
export COVIE_ENV_FILE="$env_file" COVIE_JWT_SECRET_DIR="$jwt_dir" COVIE_TLS_CERT_DIR="$tls_dir"

"$repo_root/infra/deploy/verify-runtime-env.sh" "$env_file"
[[ -r "$tls_dir/fullchain.pem" && -r "$tls_dir/privkey.pem" ]] || { echo "TLS certificate/key missing" >&2; exit 1; }

docker pull "$COVIE_API_IMAGE"
docker pull "$COVIE_WEB_IMAGE"
docker compose -f "$compose" config >/dev/null
docker compose -f "$compose" up -d --remove-orphans

for _ in $(seq 1 60); do
  if curl --fail --silent --max-time 5 "$public_url/api/v1/health" >/dev/null 2>&1; then break; fi
  sleep 2
done
"$repo_root/infra/deploy/smoke.sh" "$public_url"

old_current="$(mktemp "$state_dir/.current.XXXXXX")"
if [[ -f "$current" ]]; then cp -f "$current" "$old_current"; else : > "$old_current"; fi
cp -f "$previous" "$current.tmp"
mv "$current.tmp" "$current"
if [[ -s "$old_current" ]]; then mv "$old_current" "$previous"; else rm -f "$old_current" "$previous"; fi

echo "Application rollback completed. Database migrations were not reverted."
