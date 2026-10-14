#!/usr/bin/env bash
# Controlled Phase 4 verification; runs only against explicitly authorized staging.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "${COVIE_SOCIAL_PHASE4_CONFIRM:-}" == "STAGING_ONLY" ]] || {
  echo "Require COVIE_SOCIAL_PHASE4_CONFIRM=STAGING_ONLY" >&2; exit 2;
}
: "${COVIE_STAGING_BASE_URL:?Set the exact HTTPS staging origin}"
: "${COVIE_SOCIAL_PHASE4_ITEM_ID:?Set a published staging item ID}"
: "${COVIE_SOCIAL_E2E_SEARCH_PATH:?Set a staging /search URL path with filters/location}"
: "${COVIE_SOCIAL_E2E_ITEM_NAME:?Set the exact published catalog fixture name}"
: "${COVIE_SOCIAL_LOAD_ITEM_IDS:?Set 1 to 40 visible item UUIDs for perf}"
for key in STAGING_BUYER_EMAIL STAGING_BUYER_PASSWORD STAGING_SELLER_EMAIL STAGING_SELLER_PASSWORD STAGING_ADMIN_EMAIL STAGING_ADMIN_PASSWORD; do
  [[ -n "${!key:-}" ]] || { echo "Missing required staging test identity field: $key" >&2; exit 2; }
done
[[ "${COVIE_SOCIAL_E2E_MUTATIONS_OK:-}" == "1" ]] || {
  echo "Full acceptance requires COVIE_SOCIAL_E2E_MUTATIONS_OK=1 on disposable staging" >&2; exit 2;
}
[[ "$COVIE_STAGING_BASE_URL" == https://* ]] || {
  echo "Non-HTTPS origin is forbidden" >&2; exit 2;
}
host="${COVIE_STAGING_BASE_URL#https://}"; host="${host%%/*}"
case "$host" in
  covie.vn|www.covie.vn) echo "Production origin forbidden" >&2; exit 2 ;;
esac
[[ "$COVIE_STAGING_BASE_URL" == "https://$host" ]] || {
  echo "Expected an HTTPS origin only, without path or trailing slash" >&2; exit 2;
}
[[ "${COVIE_SOCIAL_E2E_SEARCH_PATH}" == /search* ]] || {
  echo "Only a /search staging discovery fixture path is accepted" >&2; exit 2;
}

export COVIE_SOCIAL_PHASE4_BASE_URL="$COVIE_STAGING_BASE_URL"
evidence="${COVIE_SOCIAL_PHASE4_EVIDENCE_DIR:-$repo_root/build/social-phase4/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$evidence"
sha="$(git -C "$repo_root" rev-parse HEAD)"
: "${COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST:?Provide the immutable staging candidate release manifest}"
: "${COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST:?Provide the manifest read from the running staging host}"
[[ -r "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" && -r "$COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST" ]] || {
  echo "Cannot read both release identity manifests" >&2; exit 2;
}
cmp -s "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" "$COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST" || {
  echo "Active staging release is not the intended immutable release" >&2; exit 1;
}
candidate_sha="$(sed -n 's/^GIT_SHA=//p' "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" | tail -1)"
[[ "$candidate_sha" == "$sha" ]] || {
  echo "Staging candidate GIT_SHA does not match tested checkout" >&2; exit 1;
}
grep -Eq '^API_IMAGE=.+@sha256:[0-9a-f]{64}
printf 'phase=4\nhead=%s\nstaging_origin=%s\nstarted_utc=%s\nstatus=IN_PROGRESS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"

bash "$repo_root/infra/scripts/social-phase4-smoke.sh" | tee "$evidence/readonly-smoke.log"
node --test "$repo_root/infra/perf/social-scenarios.test.mjs" | tee "$evidence/perf-scenarios.log"
for scenario in public-social-summary public-social-comments; do
  node "$repo_root/infra/perf/run-load.mjs"     --base-url "$COVIE_STAGING_BASE_URL"     --scenario "$scenario"     --concurrency "${COVIE_SOCIAL_PHASE4_CONCURRENCY:-10}"     --duration "${COVIE_SOCIAL_PHASE4_DURATION:-30s}"     | tee "$evidence/perf-$scenario.json"
done

# The staging fixture refuses the production origin and uses cookie-based role login.
# Set COVIE_SOCIAL_E2E_MUTATIONS_OK=1 only in a disposable staging account.
pnpm --dir "$repo_root/apps/web" exec playwright test e2e/social-phase4.staging.spec.ts \
  --project=chromium --workers=1 --reporter=list | tee "$evidence/playwright.log"
if grep -Eq '[0-9]+ skipped|[0-9]+ did not run' "$evidence/playwright.log"; then
  echo "Playwright skipped tests: acceptance is incomplete" >&2
  exit 1
fi
printf 'phase=4\nhead=%s\nstaging_origin=%s\ncompleted_utc=%s\nstatus=AUTOMATED_PASS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"
echo "Automated staging gate passed. Manual visual, moderation and rollback evidence remains mandatory."
echo "Evidence path: $evidence"
 "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" || {
  echo "Candidate API image is not digest-pinned" >&2; exit 1;
}
grep -Eq '^WEB_IMAGE=.+@sha256:[0-9a-f]{64}
printf 'phase=4\nhead=%s\nstaging_origin=%s\nstarted_utc=%s\nstatus=IN_PROGRESS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"

bash "$repo_root/infra/scripts/social-phase4-smoke.sh" | tee "$evidence/readonly-smoke.log"
node --test "$repo_root/infra/perf/social-scenarios.test.mjs" | tee "$evidence/perf-scenarios.log"
for scenario in public-social-summary public-social-comments; do
  node "$repo_root/infra/perf/run-load.mjs"     --base-url "$COVIE_STAGING_BASE_URL"     --scenario "$scenario"     --concurrency "${COVIE_SOCIAL_PHASE4_CONCURRENCY:-10}"     --duration "${COVIE_SOCIAL_PHASE4_DURATION:-30s}"     | tee "$evidence/perf-$scenario.json"
done

# The staging fixture refuses the production origin and uses cookie-based role login.
# Set COVIE_SOCIAL_E2E_MUTATIONS_OK=1 only in a disposable staging account.
pnpm --dir "$repo_root/apps/web" exec playwright test e2e/social-phase4.staging.spec.ts \
  --project=chromium --workers=1 --reporter=list | tee "$evidence/playwright.log"
if grep -Eq '[0-9]+ skipped|[0-9]+ did not run' "$evidence/playwright.log"; then
  echo "Playwright skipped tests: acceptance is incomplete" >&2
  exit 1
fi
printf 'phase=4\nhead=%s\nstaging_origin=%s\ncompleted_utc=%s\nstatus=AUTOMATED_PASS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"
echo "Automated staging gate passed. Manual visual, moderation and rollback evidence remains mandatory."
echo "Evidence path: $evidence"
 "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" || {
  echo "Candidate Web image is not digest-pinned" >&2; exit 1;
}
grep -Eq '^HIGHEST_FLYWAY=V019
printf 'phase=4\nhead=%s\nstaging_origin=%s\nstarted_utc=%s\nstatus=IN_PROGRESS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"

bash "$repo_root/infra/scripts/social-phase4-smoke.sh" | tee "$evidence/readonly-smoke.log"
node --test "$repo_root/infra/perf/social-scenarios.test.mjs" | tee "$evidence/perf-scenarios.log"
for scenario in public-social-summary public-social-comments; do
  node "$repo_root/infra/perf/run-load.mjs"     --base-url "$COVIE_STAGING_BASE_URL"     --scenario "$scenario"     --concurrency "${COVIE_SOCIAL_PHASE4_CONCURRENCY:-10}"     --duration "${COVIE_SOCIAL_PHASE4_DURATION:-30s}"     | tee "$evidence/perf-$scenario.json"
done

# The staging fixture refuses the production origin and uses cookie-based role login.
# Set COVIE_SOCIAL_E2E_MUTATIONS_OK=1 only in a disposable staging account.
pnpm --dir "$repo_root/apps/web" exec playwright test e2e/social-phase4.staging.spec.ts \
  --project=chromium --workers=1 --reporter=list | tee "$evidence/playwright.log"
if grep -Eq '[0-9]+ skipped|[0-9]+ did not run' "$evidence/playwright.log"; then
  echo "Playwright skipped tests: acceptance is incomplete" >&2
  exit 1
fi
printf 'phase=4\nhead=%s\nstaging_origin=%s\ncompleted_utc=%s\nstatus=AUTOMATED_PASS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"
echo "Automated staging gate passed. Manual visual, moderation and rollback evidence remains mandatory."
echo "Evidence path: $evidence"
 "$COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST" || {
  echo "V019 must be applied before social Phase 4 acceptance" >&2; exit 1;
}
printf 'phase=4\nhead=%s\nstaging_origin=%s\nstarted_utc=%s\nstatus=IN_PROGRESS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"

bash "$repo_root/infra/scripts/social-phase4-smoke.sh" | tee "$evidence/readonly-smoke.log"
node --test "$repo_root/infra/perf/social-scenarios.test.mjs" | tee "$evidence/perf-scenarios.log"
for scenario in public-social-summary public-social-comments; do
  node "$repo_root/infra/perf/run-load.mjs"     --base-url "$COVIE_STAGING_BASE_URL"     --scenario "$scenario"     --concurrency "${COVIE_SOCIAL_PHASE4_CONCURRENCY:-10}"     --duration "${COVIE_SOCIAL_PHASE4_DURATION:-30s}"     | tee "$evidence/perf-$scenario.json"
done

# The staging fixture refuses the production origin and uses cookie-based role login.
# Set COVIE_SOCIAL_E2E_MUTATIONS_OK=1 only in a disposable staging account.
pnpm --dir "$repo_root/apps/web" exec playwright test e2e/social-phase4.staging.spec.ts \
  --project=chromium --workers=1 --reporter=list | tee "$evidence/playwright.log"
if grep -Eq '[0-9]+ skipped|[0-9]+ did not run' "$evidence/playwright.log"; then
  echo "Playwright skipped tests: acceptance is incomplete" >&2
  exit 1
fi
printf 'phase=4\nhead=%s\nstaging_origin=%s\ncompleted_utc=%s\nstatus=AUTOMATED_PASS\n'   "$sha" "$COVIE_STAGING_BASE_URL" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence/manifest.txt"
echo "Automated staging gate passed. Manual visual, moderation and rollback evidence remains mandatory."
echo "Evidence path: $evidence"
