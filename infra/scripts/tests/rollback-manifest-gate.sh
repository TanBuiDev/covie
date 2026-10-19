#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd -P)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
mkdir -p "$fixture/packages/api-client/openapi" "$fixture/apps/api/src/main/resources/db/migration" "$fixture/build/sbom"
printf '{}\n' > "$fixture/packages/api-client/openapi/openapi.json"
printf '{}\n' > "$fixture/build/sbom/api.cdx.json"
printf '{}\n' > "$fixture/build/sbom/web.cdx.json"
touch "$fixture/apps/api/src/main/resources/db/migration/V025__fixture.sql"
printf 'Local fixture attestation, not a real release rehearsal\n' > "$fixture/evidence.log"
export COVIE_API_IMAGE="fixture/current@sha256:$(printf '%064d' 1)"
export COVIE_WEB_IMAGE="fixture/web@sha256:$(printf '%064d' 2)"
export GIT_SHA="$(printf '%040d' 3)"
unset COVIE_VERIFIED_ROLLBACK_API_IMAGE COVIE_ROLLBACK_EVIDENCE COVIE_RELEASE_MANIFEST_OUT COVIE_API_SBOM COVIE_WEB_SBOM
cd "$fixture"
bash "$root/infra/scripts/create-release-manifest.sh"
grep -qx 'ROLLBACK_API_IMAGE=' build/release/release-manifest.env
grep -qx 'ROLLBACK_EVIDENCE_SHA256=' build/release/release-manifest.env
export COVIE_VERIFIED_ROLLBACK_API_IMAGE="fixture/target@sha256:$(printf '%064d' 4)"
export COVIE_ROLLBACK_EVIDENCE="$fixture/evidence.log"
bash "$root/infra/scripts/create-release-manifest.sh"
expected="$(sha256sum evidence.log | awk '{print $1}')"
grep -qx "ROLLBACK_API_IMAGE=$COVIE_VERIFIED_ROLLBACK_API_IMAGE" build/release/release-manifest.env
grep -qx "ROLLBACK_EVIDENCE_SHA256=$expected" build/release/release-manifest.env
cp build/release/release-manifest.env accepted.env
export COVIE_ROLLBACK_EVIDENCE="$fixture/missing.log"
if bash "$root/infra/scripts/create-release-manifest.sh"; then echo 'FAIL: missing evidence accepted'; exit 1; fi
cmp accepted.env build/release/release-manifest.env
export COVIE_ROLLBACK_EVIDENCE="$fixture/evidence.log" COVIE_VERIFIED_ROLLBACK_API_IMAGE='fixture:mutable'
if bash "$root/infra/scripts/create-release-manifest.sh"; then echo 'FAIL: mutable target accepted'; exit 1; fi
cmp accepted.env build/release/release-manifest.env
echo 'PASS: absent attestation, exact target/evidence hash, invalid inputs preserve manifest (fixtures only)'
