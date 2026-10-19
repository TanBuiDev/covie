#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export COVIE_STORAGE_BUCKET=covie-test-only
export COVIE_STORAGE_ENDPOINT=https://storage.test.invalid
export STUB_PUBLIC_BLOCK=$'True\tTrue\tTrue\tTrue'
export STUB_PUBLIC_ERROR=0

aws() {
  [[ "$1" == s3api ]] || return 90
  case "$2" in
    get-public-access-block)
      [[ "$STUB_PUBLIC_ERROR" == 0 ]] || return 91
      printf '%s\n' "$STUB_PUBLIC_BLOCK"
      ;;
    get-bucket-versioning) printf 'Enabled\n' ;;
    get-bucket-encryption|get-bucket-cors|get-bucket-lifecycle-configuration) printf '{}\n' ;;
    *) return 92 ;;
  esac
}
export -f aws

bash "$repo_root/infra/storage/verify-s3-hardening.sh"
for unsafe in $'False\tTrue\tTrue\tTrue' $'True\tFalse\tTrue\tTrue' \
  $'True\tTrue\tFalse\tTrue' $'True\tTrue\tTrue\tFalse' $'True\tTrue\tTrue\tNone'; do
  export STUB_PUBLIC_BLOCK="$unsafe"
  if bash "$repo_root/infra/storage/verify-s3-hardening.sh" >/dev/null 2>&1; then
    echo "FAIL: verifier accepted unsafe/missing public access block: $unsafe" >&2
    exit 1
  fi
done
export STUB_PUBLIC_BLOCK=$'True\tTrue\tTrue\tTrue'
export STUB_PUBLIC_ERROR=1
if bash "$repo_root/infra/storage/verify-s3-hardening.sh" >/dev/null 2>&1; then
  echo "FAIL: verifier accepted a failed public access block read" >&2
  exit 1
fi
echo "PASS: complete public block accepted; each false/missing flag and failed read rejected"
