#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT_DIR/infra/scripts/verify-secret-hygiene.sh"
FIXTURE_DIR="$ROOT_DIR/infra/scripts/test-fixtures"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

mkdir -p "$TEMP_DIR/bin"
ln -s "$FIXTURE_DIR/fake-git.sh" "$TEMP_DIR/bin/git"
ln -s "$FIXTURE_DIR/fake-grep.sh" "$TEMP_DIR/bin/grep"

assert_status() {
  local expected_status="$1"
  local case_name="$2"
  shift 2

  set +e
  PATH="$TEMP_DIR/bin:$PATH" "$@" >/dev/null 2>&1
  local actual_status=$?
  set -e

  if [[ "$actual_status" -ne "$expected_status" ]]; then
    echo "$case_name: expected exit $expected_status, got $actual_status" >&2
    exit 1
  fi
}

assert_status 1 "matched sentinel is rejected" env FAKE_GIT_MODE=match "$SCRIPT"
assert_status 0 "scanner no-match is accepted" env FAKE_GIT_MODE=no-match "$SCRIPT"
assert_status 2 "scanner error propagates" env FAKE_GIT_MODE=scanner-error "$SCRIPT"
assert_status 2 "pipeline grep error propagates" env FAKE_GIT_MODE=no-match FAKE_GREP_STATUS=2 "$SCRIPT"

echo "Secret-hygiene status handling tests passed."
