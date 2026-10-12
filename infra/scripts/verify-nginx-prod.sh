#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
nginx="$root/infra/docker/nginx.prod.conf"
compose="$root/infra/compose/compose.prod.yml"

grep -q 'listen 443 ssl' "$nginx"
grep -q 'return 301 https://' "$nginx"
grep -q 'location /api/v1/' "$nginx"
grep -q 'proxy_pass http://covie_api' "$nginx"
for config in "$nginx" "$root/infra/docker/nginx.conf"; do
  awk '
    /location = \/api\/v1\/auth\/ssr-refresh[[:space:]]*\{/ { bridge = 1 }
    bridge && /proxy_pass http:\/\/covie_web;/ { routed = 1 }
    bridge && /\}/ { bridge = 0 }
    END { exit !routed }
  ' "$config" || { echo "SSR refresh must route to Next.js: $config" >&2; exit 1; }
done
grep -q 'location = /api/v1/me/events' "$nginx"
grep -q 'proxy_buffering off' "$nginx"
grep -q 'client_max_body_size 2m' "$nginx"
grep -q 'security-headers.conf' "$nginx"
awk '
  /location[^\{]*\{/ { proxy = 0; stripped = 0 }
  /proxy_pass/ { proxy = 1 }
  /proxy_set_header[[:space:]]+Forwarded[[:space:]]+"";/ { stripped = 1 }
  /^[[:space:]]*\}/ { if (proxy && !stripped) exit 1; proxy = 0 }
' "$nginx" || { echo "Production proxy locations must strip client Forwarded headers" >&2; exit 1; }
if grep -Eq 'location[[:space:]]+[^;]*actuator|proxy_pass[^;]*9091' "$nginx"; then
  echo "Public Nginx must not proxy Actuator/management" >&2
  exit 1
fi
published="$(grep -nE '^[[:space:]]+ports:' "$compose" | wc -l | tr -d ' ')"
[[ "$published" == "1" ]] || { echo "Only Nginx may publish host ports" >&2; exit 1; }

echo "Production Nginx/Compose static contract OK."
if command -v docker >/dev/null 2>&1 && [[ "${COVIE_VERIFY_COMPOSE_CONFIG:-false}" == "true" ]]; then
  docker compose -f "$compose" config >/dev/null
  echo "docker compose config OK."
fi
