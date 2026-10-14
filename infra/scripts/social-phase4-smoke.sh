#!/usr/bin/env bash
# Read-only social release smoke. Never logs tokens or uses authenticated sessions.
set -euo pipefail

: "${COVIE_SOCIAL_PHASE4_BASE_URL:?Use explicit HTTPS staging origin}"
: "${COVIE_SOCIAL_PHASE4_ITEM_ID:?Use a published staging catalog item UUID}"
base="${COVIE_SOCIAL_PHASE4_BASE_URL%/}"
id="$COVIE_SOCIAL_PHASE4_ITEM_ID"
[[ "$base" == https://* ]] || { echo "HTTPS staging is mandatory" >&2; exit 2; }
host="${base#https://}"; host="${host%%/*}"
case "$host" in
  covie.vn|www.covie.vn) echo "Production origin is forbidden for Phase 4 rehearsal" >&2; exit 2 ;;
esac
[[ "$host" != *'@'* && "$host" != *':'* && "$base" == "https://$host" ]] || {
  echo "Only an HTTPS staging origin without credentials, ports or paths is accepted" >&2; exit 2;
}
[[ "$id" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || {
  echo "A valid fixture UUID is required" >&2; exit 2;
}

body="$(mktemp)"
trap 'rm -f "$body"' EXIT
echo "Phase 4 social read-only staging smoke: $host"
curl --fail --silent --show-error --max-time 10 --get   --data-urlencode "itemIds=$id" "$base/api/v1/catalog/social-summary" > "$body"
SOCIAL_RESPONSE_FILE="$body" SOCIAL_ITEM_ID="$id" node --input-type=module -e '
import { readFileSync } from "node:fs";
const result = JSON.parse(readFileSync(process.env.SOCIAL_RESPONSE_FILE, "utf8"));
const s = result[process.env.SOCIAL_ITEM_ID];
if (!s || !Number.isFinite(s.likes) || !Number.isFinite(s.comments) ||
  typeof s.liked !== "boolean" || typeof s.saved !== "boolean" ||
  s.liked || s.saved) {
  throw Error("Public anonymous social summary has invalid or private viewer state.");
}'
curl --fail --silent --show-error --max-time 10   "$base/api/v1/catalog/items/$id/comments?limit=5" > "$body"
SOCIAL_RESPONSE_FILE="$body" node --input-type=module -e '
import { readFileSync } from "node:fs";
const result = JSON.parse(readFileSync(process.env.SOCIAL_RESPONSE_FILE, "utf8"));
if (!Array.isArray(result.items)) throw Error("Malformed social comment page");'

status="$(curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}'   --request PUT "$base/api/v1/me/catalog/items/$id/likes")"
[[ "$status" == "401" || "$status" == "403" ]] || {
  echo "Security regression: unauthenticated like returned HTTP $status" >&2; exit 1;
}
status="$(curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}'   "$base/api/v1/admin/catalog-social/comments/00000000-0000-4000-8000-000000000001")"
[[ "$status" == "401" || "$status" == "403" ]] || {
  echo "Security regression: moderator-only preview returned HTTP $status" >&2; exit 1;
}
echo "Phase 4 read-only social staging smoke PASS ($host)"
