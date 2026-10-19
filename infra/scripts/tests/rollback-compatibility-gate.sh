#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd -P)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
mkdir -p "$fixture/infra/deploy" "$fixture/state" "$fixture/bin" "$fixture/tls"
cp "$root/infra/deploy/rollback.sh" "$fixture/infra/deploy/"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/infra/deploy/verify-runtime-env.sh"
cp "$fixture/infra/deploy/verify-runtime-env.sh" "$fixture/infra/deploy/smoke.sh"
chmod +x "$fixture/infra/deploy/"*.sh
touch "$fixture/tls/fullchain.pem" "$fixture/tls/privkey.pem"
cat > "$fixture/bin/docker" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "$ROLLBACK_TEST_CALLS"
STUB
printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/bin/curl"
chmod +x "$fixture/bin/"*
export PATH="$fixture/bin:$PATH" COVIE_RELEASE_STATE_DIR="$fixture/state"
export COVIE_TLS_CERT_DIR="$fixture/tls" ROLLBACK_TEST_CALLS="$fixture/calls"
source_image="fixture/current@sha256:$(printf '%064d' 1)"
target_image="fixture/previous@sha256:$(printf '%064d' 2)"
web_image="fixture/web@sha256:$(printf '%064d' 3)"
evidence_sha="$(printf '%064d' 4)"
reset_manifests() {
  printf 'API_IMAGE=%s\nWEB_IMAGE=%s\nHIGHEST_FLYWAY=V022\n' "$target_image" "$web_image" > "$fixture/state/previous.env"
  printf 'API_IMAGE=%s\nWEB_IMAGE=%s\nHIGHEST_FLYWAY=V025\n' "$source_image" "$web_image" > "$fixture/state/current.env"
  : > "$fixture/calls"
}
reject() {
  cp "$fixture/state/previous.env" "$fixture/previous.snapshot"
  if bash "$fixture/infra/deploy/rollback.sh" > "$fixture/output" 2>&1; then
    echo 'FAIL: unverified video rollback accepted' >&2; exit 1
  fi
  [[ ! -s "$fixture/calls" ]] || { echo 'FAIL: Docker called before compatibility rejection'; exit 1; }
  cmp "$fixture/state/previous.env" "$fixture/previous.snapshot"
}
reset_manifests
reject
reset_manifests
rm "$fixture/state/current.env"
reject
reset_manifests
printf 'ROLLBACK_API_IMAGE=%s\nROLLBACK_EVIDENCE_SHA256=not-a-hash\n' "$target_image" >> "$fixture/state/current.env"
reject
reset_manifests
printf 'ROLLBACK_API_IMAGE=%s\nROLLBACK_EVIDENCE_SHA256=%s\n' "$source_image" "$evidence_sha" >> "$fixture/state/current.env"
reject
reset_manifests
printf 'ROLLBACK_API_IMAGE=%s\n' "$target_image" >> "$fixture/state/current.env"
reject
reset_manifests
cp "$fixture/state/current.env" "$fixture/candidate.env"
printf 'ROLLBACK_API_IMAGE=%s\nROLLBACK_EVIDENCE_SHA256=%s\n' "$target_image" "$evidence_sha" >> "$fixture/state/current.env"
COVIE_ROLLBACK_FROM_MANIFEST="$fixture/candidate.env" reject
reset_manifests
printf 'ROLLBACK_API_IMAGE=%s\nROLLBACK_EVIDENCE_SHA256=%s\n' "$target_image" "$evidence_sha" >> "$fixture/state/current.env"
bash "$fixture/infra/deploy/rollback.sh"
grep -q '^compose .*up -d' "$fixture/calls"
grep -qx "API_IMAGE=$target_image" "$fixture/state/current.env"
reset_manifests
sed -i 's/HIGHEST_FLYWAY=V025/HIGHEST_FLYWAY=V022/' "$fixture/state/current.env"
bash "$fixture/infra/deploy/rollback.sh"
grep -q '^compose .*up -d' "$fixture/calls"
echo 'PASS: video compatibility attestations fail closed before Docker; candidate trap source and legacy rollback preserved (stubs only)'
