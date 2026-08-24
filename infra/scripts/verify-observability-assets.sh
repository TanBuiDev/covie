#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
prom="$root/infra/observability/prometheus/prometheus.yml"
rules="$root/infra/observability/prometheus/rules/covie.rules.yml"
alert="$root/infra/observability/alertmanager/alertmanager.yml"

grep -q 'api:9091' "$prom"
grep -q '/actuator/prometheus' "$prom"
grep -q 'CoVieApiDown' "$rules"
grep -q 'CoVieBackupTooOld' "$rules"
grep -q 'url_file:' "$alert"
if grep -Eq 'https?://[^[:space:]]+webhook|hooks\.(slack|discord)' "$alert"; then
  echo "Webhook must be supplied through url_file, not committed" >&2
  exit 1
fi

echo "Observability asset contract OK."
