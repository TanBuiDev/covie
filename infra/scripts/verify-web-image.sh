#!/usr/bin/env bash
set -euo pipefail
image="${1:-covie-web:plan10}"
user="$(docker image inspect "$image" --format '{{.Config.User}}')"
if [[ -z "$user" || "$user" == "0" || "$user" == "root" || "$user" == 0:* ]]; then
  echo "Web image must run as non-root" >&2
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
for forbidden in /app/.git /app/.env /app/apps/web/src; do
  if grep -Fx -e "${forbidden#/}" -e "${forbidden#/}/" "$listing" >/dev/null; then
    echo "Forbidden runtime artifact found: $forbidden" >&2
    exit 1
  fi
done
name="covie-web-plan10-verify-$$"
docker run -d --rm --name "$name" -e COVIE_PUBLIC_URL=http://127.0.0.1:33000 -p 33000:3000 "$image" >/dev/null
trap 'docker rm -f "$name" >/dev/null 2>&1 || true; docker rm -f "$cid" >/dev/null 2>&1 || true; rm -f "$listing"' EXIT
for _ in {1..60}; do
  if curl --fail --silent http://127.0.0.1:33000/ >/dev/null; then
    for asset in /icon.svg /figma/covie-market-pin.svg /sw.js /offline.html; do
      if ! curl --fail --silent "http://127.0.0.1:33000$asset" >/dev/null; then
        echo "Web image public asset missing: $asset" >&2
        exit 1
      fi
    done
    echo "Web image runtime contract OK (user=$user)."
    exit 0
  fi
  sleep 1
done
echo "Web image runtime contract failed" >&2
exit 1
