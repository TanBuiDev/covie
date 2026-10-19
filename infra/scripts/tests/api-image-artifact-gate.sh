#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd -P)"
mkdir -p "$root/.local"
fixture="$(mktemp -d "$root/.local/api-image-artifact-gate.XXXXXX")"
fixture="$(cd "$fixture" && pwd -P)"
cleanup() {
  case "$fixture" in
    "$root"/.local/api-image-artifact-gate.*) rm -rf -- "$fixture" ;;
    *) echo 'Refusing cleanup outside the named test fixture' >&2; return 1 ;;
  esac
}
trap cleanup EXIT
mkdir -p "$fixture/bin" "$fixture/tree/app/.git" "$fixture/tree/app/src" "$fixture/tree/run/covie-secrets"
printf 'disposable fixture, not a credential\n' > "$fixture/tree/app/.env"
printf 'fixture\n' > "$fixture/tree/app/.git/config"
printf 'fixture\n' > "$fixture/tree/app/src/Example.java"
printf 'disposable fixture, not a key\n' > "$fixture/tree/run/covie-secrets/jwt-private.pem"
cat > "$fixture/bin/docker" <<'STUB'
#!/usr/bin/env bash
case "$1" in
  image) printf '10001:10001\n' ;;
  create) printf 'fixture-container\n' ;;
  export)
    [[ "${API_IMAGE_TEST_EXPORT_EXIT:-0}" == 0 ]] || exit "$API_IMAGE_TEST_EXPORT_EXIT"
    cat "$API_IMAGE_TEST_ARCHIVE" ;;
  rm) exit 0 ;;
  *) exit 2 ;;
esac
STUB
chmod +x "$fixture/bin/docker"
export PATH="$fixture/bin:$PATH" API_IMAGE_TEST_ARCHIVE="$fixture/export.tar" COVIE_VERIFY_API_RUNTIME=false
failures=0
for forbidden in app/.env app/.git app/src run/covie-secrets/jwt-private.pem; do
  tar -cf "$fixture/export.tar" -C "$fixture/tree" "$forbidden"
  if bash "$root/infra/scripts/verify-api-image.sh" fixture-image > "$fixture/output.log" 2>&1; then
    echo "FAIL: forbidden artifact accepted: $forbidden" >&2
    failures=$((failures + 1))
  elif ! grep -q 'Forbidden runtime artifact found:' "$fixture/output.log"; then
    echo "FAIL: artifact rejected for an unrelated reason: $forbidden" >&2
    failures=$((failures + 1))
  fi
done
tar -cf "$fixture/export.tar" --files-from /dev/null
if API_IMAGE_TEST_EXPORT_EXIT=7 bash "$root/infra/scripts/verify-api-image.sh" fixture-image > "$fixture/output.log" 2>&1; then
  echo 'FAIL: exporter failure accepted' >&2
  failures=$((failures + 1))
elif ! grep -q 'Cannot inspect runtime image artifacts' "$fixture/output.log"; then
  echo 'FAIL: exporter failure rejected for an unrelated reason' >&2
  failures=$((failures + 1))
fi
printf 'not a tar archive\n' > "$fixture/export.tar"
if bash "$root/infra/scripts/verify-api-image.sh" fixture-image > "$fixture/output.log" 2>&1; then
  echo 'FAIL: unreadable archive accepted' >&2
  failures=$((failures + 1))
elif ! grep -q 'Cannot inspect runtime image artifacts' "$fixture/output.log"; then
  echo 'FAIL: unreadable archive rejected for an unrelated reason' >&2
  failures=$((failures + 1))
fi
tar -cf "$fixture/export.tar" --files-from /dev/null
bash "$root/infra/scripts/verify-api-image.sh" fixture-image > "$fixture/output.log" 2>&1
grep -q 'API image static contract OK' "$fixture/output.log"
grep -q 'Runtime startup/health check skipped' "$fixture/output.log"
[[ "$failures" == 0 ]] || exit 1
echo 'PASS: four forbidden paths and export/listing failures rejected; clean static-only archive accepted (isolated command stubs only)'
