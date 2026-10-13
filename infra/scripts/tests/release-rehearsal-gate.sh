#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/infra/deploy" "$fixture/infra/scripts" "$fixture/state"
cp "$root/infra/deploy/rehearse-release.sh" "$fixture/infra/deploy/"
printf 'API_IMAGE=a@sha256:abc\nWEB_IMAGE=b@sha256:def\n' > "$fixture/candidate.env"
cp "$fixture/candidate.env" "$fixture/state/current.env"
printf 'COVIE_RELEASE_STATE_DIR=%q\n' "$fixture/state" > "$fixture/config.env"
cat > "$fixture/infra/scripts/run-staging-rehearsal.sh" <<'MOCK'
#!/usr/bin/env bash
if [[ "${MOCK_DRIFT:-}" == 1 ]]; then echo changed > "$COVIE_RELEASE_STATE_DIR/current.env"; fi
exit "${MOCK_STATUS:-0}"
MOCK
run_gate() { bash "$fixture/infra/deploy/rehearse-release.sh" "$fixture/candidate.env" "$fixture/config.env"; }
run_gate
if MOCK_STATUS=7 run_gate; then echo 'FAIL: failed rehearsal accepted'; exit 1; fi
if MOCK_DRIFT=1 run_gate; then echo 'FAIL: changed release accepted'; exit 1; fi
cp "$fixture/candidate.env" "$fixture/state/current.env"
echo changed > "$fixture/candidate.env"
if run_gate; then echo 'FAIL: wrong initial release accepted'; exit 1; fi
echo 'PASS: success, failure and release drift gates'
