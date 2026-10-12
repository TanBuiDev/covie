#!/usr/bin/env bash
set -euo pipefail

TRIVY_VERSION="0.72.0"
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

run_trivy() {
  if command -v trivy >/dev/null 2>&1; then
    local actual
    actual="$(trivy --version | awk '/Version:/ {print $2; exit}')"
    [[ "$actual" == "$TRIVY_VERSION" ]] || {
      echo "Trivy $TRIVY_VERSION is required; found ${actual:-unknown}" >&2
      exit 2
    }
    trivy "$@"
  else
    command -v docker >/dev/null 2>&1 || { echo "trivy or docker is required" >&2; exit 2; }
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$repo_root:/work" -w /work \
      "aquasec/trivy:${TRIVY_VERSION}" "$@"
  fi
}

cd "$repo_root"
mkdir -p "$repo_root/build"
release_source="$(mktemp -d "$repo_root/build/security-source.XXXXXX")"
cleanup_release_source() { rm -rf "$release_source"; }
trap cleanup_release_source EXIT

# Scan exactly the release source, including uncommitted non-ignored changes,
# without traversing Git metadata, local worktrees, caches or test evidence.
git ls-files --cached --others --exclude-standard -z \
  | tar --null --files-from=- --create --file=- \
  | tar --extract --file=- --directory="$release_source"

# Dependency vulnerability checks are performed on the immutable runtime images
# below.  Scanning source POM files makes Trivy resolve remote Maven metadata,
# turning the release gate into a Maven Central availability check.
run_trivy fs --offline-scan --timeout 15m --scanners misconfig,secret --severity HIGH,CRITICAL --exit-code 1 \
  --skip-dirs '**/target' --skip-dirs '**/.next' --skip-dirs '**/node_modules' \
  --skip-files '**/Dockerfile.dockerignore' "build/$(basename "$release_source")"

if command -v pnpm >/dev/null 2>&1; then
  case "$(uname -s)" in
    MINGW*|MSYS*)
      powershell.exe -NoProfile -Command "pnpm audit --prod --audit-level=high"
      powershell.exe -NoProfile -Command "pnpm audit --audit-level=critical"
      ;;
    *)
      pnpm audit --prod --audit-level=high
      pnpm audit --audit-level=critical
      ;;
  esac
else
  echo "pnpm not installed; Node production audit must run in CI/release job" >&2
fi

if [[ -n "${COVIE_API_IMAGE:-}" ]]; then
  run_trivy image --severity HIGH,CRITICAL --exit-code 1 "$COVIE_API_IMAGE"
fi
if [[ -n "${COVIE_WEB_IMAGE:-}" ]]; then
  run_trivy image --severity HIGH,CRITICAL --exit-code 1 "$COVIE_WEB_IMAGE"
fi

# Direct production-exposure source checks. Runtime smoke adds network proof.
grep -q 'output: "standalone"' apps/web/next.config.ts
grep -q 'proxy_buffering off' infra/docker/nginx.prod.conf
grep -q 'Strict-Transport-Security' infra/docker/security-headers.conf
grep -q 'covie.rate-limit:' apps/api/src/main/resources/application-prod.yml || grep -q 'rate-limit:' apps/api/src/main/resources/application-prod.yml
! grep -Eq '0\.0\.0\.0:(8080|3000|9091)' infra/compose/compose.prod.yml

echo "Security release source gate passed"
