#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd -P)"
mkdir -p "$root/.local"
fixture="$(mktemp -d "$root/.local/web-image-artifact-gate.XXXXXX")"
fixture="$(cd "$fixture" && pwd -P)"
cleanup() {
  case "$fixture" in
    "$root"/.local/web-image-artifact-gate.*) rm -rf -- "$fixture" ;;
    *) echo 'Refusing cleanup outside the named test fixture' >&2; return 1 ;;
  esac
}
trap cleanup EXIT
mkdir -p "$fixture/bin" "$fixture/tree/app/.git" "$fixture/tree/app/apps/web/src"
printf 'disposable fixture, not a credential\n' > "$fixture/tree/app/.env"
printf 'fixture\n' > "$fixture/tree/app/.git/config"
printf 'fixture\n' > "$fixture/tree/app/apps/web/src/private.ts"
cat > "$fixture/bin/docker" <<'STUB'
#!/usr/bin/env bash
case "$1" in
  image) printf '10001:10001\n' ;;
  create|run) printf 'fixture-container\n' ;;
  export)
    [[ "${WEB_IMAGE_TEST_EXPORT_EXIT:-0}" == 0 ]] || exit "$WEB_IMAGE_TEST_EXPORT_EXIT"
    cat "$WEB_IMAGE_TEST_ARCHIVE" ;;
  rm) exit 0 ;;
  *) exit 2 ;;
esac
STUB
cat > "$fixture/bin/curl" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "$fixture/bin/docker" "$fixture/bin/curl"
export PATH="$fixture/bin:$PATH" WEB_IMAGE_TEST_ARCHIVE="$fixture/export.tar"
for forbidden in app/.env app/.git app/apps/web/src; do
  tar -cf "$fixture/export.tar" -C "$fixture/tree" "$forbidden"
  if bash "$root/infra/scripts/verify-web-image.sh" fixture-image > "$fixture/output.log" 2>&1; then
    echo "FAIL: forbidden artifact accepted: $forbidden" >&2
    exit 1
  fi
  grep -q 'Forbidden runtime artifact found:' "$fixture/output.log"
done
tar -cf "$fixture/export.tar" --files-from /dev/null
if WEB_IMAGE_TEST_EXPORT_EXIT=7 bash "$root/infra/scripts/verify-web-image.sh" fixture-image > "$fixture/output.log" 2>&1; then
  echo 'FAIL: uninspectable runtime image accepted' >&2
  exit 1
fi
grep -q 'Cannot inspect runtime image artifacts' "$fixture/output.log"
bash "$root/infra/scripts/verify-web-image.sh" fixture-image > "$fixture/output.log" 2>&1
grep -q 'Web image runtime contract OK' "$fixture/output.log"
echo 'PASS: forbidden file/directory artifacts and inspection failure rejected, clean archive accepted (isolated command stubs only)'
