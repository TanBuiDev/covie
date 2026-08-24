#!/usr/bin/env bash
set -euo pipefail

: "${COVIE_API_IMAGE:?COVIE_API_IMAGE must be an immutable image digest}"
: "${COVIE_WEB_IMAGE:?COVIE_WEB_IMAGE must be an immutable image digest}"
: "${GIT_SHA:?GIT_SHA is required}"

[[ "$COVIE_API_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "API image must use @sha256 digest" >&2; exit 2; }
[[ "$COVIE_WEB_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "Web image must use @sha256 digest" >&2; exit 2; }
[[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "GIT_SHA must be a full commit SHA" >&2; exit 2; }

out="${COVIE_RELEASE_MANIFEST_OUT:-build/release/release-manifest.env}"
mkdir -p "$(dirname "$out")"

openapi="packages/api-client/openapi/openapi.json"
[[ -f "$openapi" ]] || { echo "Missing generated OpenAPI: $openapi" >&2; exit 2; }
openapi_sha="$(sha256sum "$openapi" | awk '{print $1}')"

migration_dir="apps/api/src/main/resources/db/migration"
highest="$(find "$migration_dir" -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' \
  | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1)"
[[ -n "$highest" ]] || { echo "No Flyway migration found" >&2; exit 2; }
if (( highest < 13 )); then
  echo "Release is blocked: Plan 10 requires prerequisite V013 or later" >&2
  exit 1
fi

api_sbom="${COVIE_API_SBOM:-build/sbom/api.cdx.json}"
web_sbom="${COVIE_WEB_SBOM:-build/sbom/web.cdx.json}"
[[ -f "$api_sbom" && -f "$web_sbom" ]] || { echo "SBOM files are required before manifest creation" >&2; exit 2; }
api_sbom_sha="$(sha256sum "$api_sbom" | awk '{print $1}')"
web_sbom_sha="$(sha256sum "$web_sbom" | awk '{print $1}')"

cat > "$out.tmp" <<EOF
API_IMAGE=$COVIE_API_IMAGE
WEB_IMAGE=$COVIE_WEB_IMAGE
GIT_SHA=$GIT_SHA
OPENAPI_SHA256=$openapi_sha
HIGHEST_FLYWAY=V$(printf '%03d' "$highest")
BUILD_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)
API_SBOM_SHA256=$api_sbom_sha
WEB_SBOM_SHA256=$web_sbom_sha
EOF
chmod 0644 "$out.tmp"
mv "$out.tmp" "$out"

echo "Release manifest: $out"
