#!/usr/bin/env bash
set -euo pipefail

TRIVY_VERSION="0.72.0"
: "${COVIE_API_IMAGE:?COVIE_API_IMAGE is required}"
: "${COVIE_WEB_IMAGE:?COVIE_WEB_IMAGE is required}"
out_dir="${COVIE_SBOM_DIR:-build/sbom}"
mkdir -p "$out_dir"

run_trivy() {
  if command -v trivy >/dev/null 2>&1; then
    trivy "$@"
  else
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD:/work" -w /work \
      "aquasec/trivy:${TRIVY_VERSION}" "$@"
  fi
}

run_trivy image --format cyclonedx --output "$out_dir/api.cdx.json" "$COVIE_API_IMAGE"
run_trivy image --format cyclonedx --output "$out_dir/web.cdx.json" "$COVIE_WEB_IMAGE"
sha256sum "$out_dir/api.cdx.json" "$out_dir/web.cdx.json" > "$out_dir/SHA256SUMS"

echo "SBOM written to $out_dir"
