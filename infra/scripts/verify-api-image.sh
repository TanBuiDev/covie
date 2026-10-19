#!/usr/bin/env bash
set -euo pipefail

image="${1:-covie-api:plan10}"
user="$(docker image inspect "$image" --format '{{.Config.User}}')"
if [[ -z "$user" || "$user" == "0" || "$user" == "root" || "$user" == 0:* ]]; then
  echo "API image must run as non-root" >&2
  exit 1
fi

cid="$(docker create "$image")"
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true' EXIT
listing="$(mktemp)"
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true; rm -f "$listing"' EXIT
if ! docker export "$cid" | tar -tf - > "$listing"; then
  echo "Cannot inspect runtime image artifacts" >&2
  exit 1
fi
for forbidden in /app/.git /app/.env /app/src /run/covie-secrets/jwt-private.pem; do
  if grep -Fx -e "${forbidden#/}" -e "${forbidden#/}/" "$listing" >/dev/null; then
    echo "Forbidden runtime artifact found: $forbidden" >&2
    exit 1
  fi
done

echo "API image static contract OK (user=$user)."
if [[ "${COVIE_VERIFY_API_RUNTIME:-false}" != "true" ]]; then
  echo "Runtime startup/health check skipped; set COVIE_VERIFY_API_RUNTIME=true with disposable DB/Redis/JWT production-shaped env to execute it."
  exit 0
fi

: "${COVIE_API_RUNTIME_ENV_FILE:?COVIE_API_RUNTIME_ENV_FILE is required}"
: "${COVIE_API_RUNTIME_SECRET_DIR:?COVIE_API_RUNTIME_SECRET_DIR is required}"
name="covie-api-plan10-verify-$$"
docker run -d --rm --name "$name" --env-file "$COVIE_API_RUNTIME_ENV_FILE" \
  -v "$COVIE_API_RUNTIME_SECRET_DIR:/run/covie-secrets:ro" -p 38080:8080 "$image" >/dev/null
trap 'docker rm -f "$name" >/dev/null 2>&1 || true; docker rm -f "$cid" >/dev/null 2>&1 || true; rm -f "$listing"' EXIT
for _ in {1..60}; do
  if curl --fail --silent http://127.0.0.1:38080/api/v1/health >/dev/null; then
    echo "API runtime health contract OK."
    exit 0
  fi
  sleep 1
done
echo "API runtime health contract failed" >&2
exit 1
