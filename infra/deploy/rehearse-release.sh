#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
manifest="${1:-}"
config="${2:-/etc/covie/staging-rehearsal.env}"
[[ -r "$manifest" && -r "$config" ]] || { echo "Release manifest and staging rehearsal configuration are required" >&2; exit 2; }
snapshot="$(mktemp)"
trap 'rm -f "$snapshot"' EXIT
cp "$manifest" "$snapshot"

# Operator-managed shell configuration, containing staging-only connection variables
# and the controlled hooks required by run-staging-rehearsal.sh. Never trace secrets.
set -a
source "$config"
set +a
current="${COVIE_RELEASE_STATE_DIR:-/var/lib/covie/releases}/current.env"
cmp -s "$snapshot" "$current" || { echo "Staging is not running the candidate release manifest" >&2; exit 1; }

bash "$repo_root/infra/scripts/run-staging-rehearsal.sh"

# The rollback drill must return staging to the exact candidate before promotion.
cmp -s "$snapshot" "$current" || { echo "Staging release changed during rehearsal" >&2; exit 1; }
echo "Candidate release staging rehearsal passed."
