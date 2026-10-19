#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd -P)"
bash -n "$root/infra/scripts/run-social-phase4-staging.sh"
mkdir -p "$root/.local"
fixture="$(mktemp -d "$root/.local/phase4-runner-gate.XXXXXX")"
fixture="$(cd "$fixture" && pwd -P)"
cleanup() {
  case "$fixture" in
    "$root"/.local/phase4-runner-gate.*) rm -rf -- "$fixture" ;;
    *) echo 'Refusing cleanup outside the named test fixture' >&2; return 1 ;;
  esac
}
trap cleanup EXIT
mkdir -p "$fixture/infra/scripts" "$fixture/bin"
cp "$root/infra/scripts/run-social-phase4-staging.sh" "$fixture/infra/scripts/"
cat > "$fixture/infra/scripts/social-phase4-smoke.sh" <<'STUB'
printf 'smoke\n' >> "$PHASE4_TEST_CALLS"
exit "${PHASE4_TEST_SMOKE_EXIT:-0}"
STUB
cat > "$fixture/bin/node" <<'STUB'
#!/usr/bin/env bash
printf 'node %s\n' "$*" >> "$PHASE4_TEST_CALLS"
printf 'fixture-only node success\n'
STUB
cat > "$fixture/bin/pnpm" <<'STUB'
#!/usr/bin/env bash
printf 'pnpm\n' >> "$PHASE4_TEST_CALLS"
printf '%s\n' "${PHASE4_TEST_PLAYWRIGHT_OUTPUT:-1 passed}"
STUB
chmod +x "$fixture/bin/node" "$fixture/bin/pnpm"
export PATH="$fixture/bin:$PATH"
export COVIE_SOCIAL_PHASE4_CONFIRM=STAGING_ONLY COVIE_STAGING_BASE_URL=https://fixture.invalid
export COVIE_SOCIAL_PHASE4_ITEM_ID=11111111-1111-4111-8111-111111111111
export COVIE_SOCIAL_LOAD_ITEM_IDS="$COVIE_SOCIAL_PHASE4_ITEM_ID"
export COVIE_SOCIAL_E2E_SEARCH_PATH='/search?fixture=1' COVIE_SOCIAL_E2E_ITEM_NAME=fixture
export COVIE_SOCIAL_E2E_MUTATIONS_OK=1
export STAGING_BUYER_EMAIL=fixture STAGING_BUYER_PASSWORD=not-a-credential
export STAGING_SELLER_EMAIL=fixture STAGING_SELLER_PASSWORD=not-a-credential
export STAGING_ADMIN_EMAIL=fixture STAGING_ADMIN_PASSWORD=not-a-credential
export COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST="$fixture/candidate.env"
export COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST="$fixture/active.env"
export COVIE_SOCIAL_PHASE4_EVIDENCE_DIR="$fixture/evidence" PHASE4_TEST_CALLS="$fixture/calls"
sha="$(git -C "$root" rev-parse HEAD)"
digest="$(printf 'a%.0s' {1..64})"
manifest() {
  printf 'GIT_SHA=%s\nAPI_IMAGE=%s\nWEB_IMAGE=%s\nHIGHEST_FLYWAY=%s\n' \
    "$sha" "${1:-fixture/api@sha256:$digest}" "${2:-fixture/web@sha256:$digest}" "${3:-V019}" > "$fixture/candidate.env"
  cp "$fixture/candidate.env" "$fixture/active.env"
  : > "$fixture/calls"
}
run_gate() { bash "$fixture/infra/scripts/run-social-phase4-staging.sh" > "$fixture/output.log" 2>&1; }
reject_before_commands() {
  if run_gate; then echo 'FAIL: invalid manifest accepted'; exit 1; fi
  [[ ! -s "$fixture/calls" ]] || { echo 'FAIL: commands ran before manifest rejection'; exit 1; }
}
manifest
run_gate
[[ "$(wc -l < "$fixture/calls" | tr -d ' ')" == 5 ]] || { echo 'FAIL: duplicated or missing gate commands'; exit 1; }
grep -q '^status=AUTOMATED_PASS$' "$fixture/evidence/manifest.txt"
manifest "fixture/api:mutable"
reject_before_commands
manifest "fixture/api@sha256:$digest" "fixture/web:mutable"
reject_before_commands
manifest "fixture/api@sha256:$digest" "fixture/web@sha256:$digest" V018
reject_before_commands
manifest "fixture/api@sha256:$digest" "fixture/web@sha256:$digest" V022
run_gate
manifest
if PHASE4_TEST_PLAYWRIGHT_OUTPUT='1 skipped' run_gate; then echo 'FAIL: skipped browser acceptance accepted'; exit 1; fi
if grep -q '^status=AUTOMATED_PASS$' "$fixture/evidence/manifest.txt"; then echo 'FAIL: skip wrote PASS'; exit 1; fi
manifest
if PHASE4_TEST_SMOKE_EXIT=7 run_gate; then echo 'FAIL: downstream failure accepted'; exit 1; fi
if grep -q '^status=AUTOMATED_PASS$' "$fixture/evidence/manifest.txt"; then echo 'FAIL: failed smoke wrote PASS'; exit 1; fi
echo 'PASS: runner syntax, one execution, pinned manifests, migration prerequisite, skip/failure rejection (isolated stubs only)'
