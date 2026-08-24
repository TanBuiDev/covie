#!/usr/bin/env bash
set -euo pipefail

base="${1:-${COVIE_PUBLIC_URL:-https://covie.vn}}"
[[ "$base" == https://* ]] || { echo "smoke target must be HTTPS" >&2; exit 2; }
base="${base%/}"
host="${base#https://}"
host="${host%%/*}"

headers="$(mktemp)"
trap 'rm -f "$headers"' EXIT

curl --fail --silent --show-error --max-time 10 -D "$headers" -o /dev/null "$base/"
grep -qi '^strict-transport-security:' "$headers"
grep -qi '^x-content-type-options:[[:space:]]*nosniff' "$headers"
grep -qi '^x-frame-options:[[:space:]]*DENY' "$headers"

curl --fail --silent --show-error --max-time 10 "$base/api/v1/health" >/dev/null
curl --fail --silent --show-error --max-time 10 "$base/api/v1/catalog/items?limit=1" >/dev/null

actuator_code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 "$base/actuator/health")"
[[ "$actuator_code" == "404" || "$actuator_code" == "403" ]] || {
  echo "Public actuator exposure check failed: HTTP $actuator_code" >&2; exit 1;
}

sse_code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 "$base/api/v1/me/events" || true)"
[[ "$sse_code" == "401" || "$sse_code" == "403" ]] || {
  echo "Anonymous SSE must be rejected; got HTTP $sse_code" >&2; exit 1;
}

redirect="$(curl --silent --output /dev/null --write-out '%{http_code} %{redirect_url}' --max-time 10 "http://$host/")"
[[ "$redirect" == 301\ https://* || "$redirect" == 308\ https://* ]] || {
  echo "HTTP to HTTPS redirect missing: $redirect" >&2; exit 1;
}

if command -v docker >/dev/null 2>&1; then
  exposed="$(docker ps --format '{{.Names}} {{.Ports}}' | grep -E 'covie.*(8080|3000|9091)->' || true)"
  [[ -z "$exposed" ]] || { echo "Direct API/Web/management host port exposure detected" >&2; exit 1; }
fi

echo "Production smoke passed for $base"
