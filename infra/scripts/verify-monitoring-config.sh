#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

command -v docker >/dev/null 2>&1 || { echo "docker is required to validate pinned monitoring configs" >&2; exit 2; }

docker run --rm \
  --entrypoint promtool \
  -v "$root/infra/observability/prometheus:/etc/covie-prometheus:ro" \
  prom/prometheus:v3.11.3 \
  check config /etc/covie-prometheus/prometheus.yml

docker run --rm \
  --entrypoint promtool \
  -v "$root/infra/observability/prometheus:/etc/covie-prometheus:ro" \
  prom/prometheus:v3.11.3 \
  check rules /etc/covie-prometheus/rules/covie.rules.yml

if [[ -f infra/observability/alertmanager/alertmanager.yml ]]; then
  docker run --rm \
    --entrypoint amtool \
    -v "$root/infra/observability/alertmanager:/etc/covie-alertmanager:ro" \
    prom/alertmanager:v0.32.1 \
    check-config /etc/covie-alertmanager/alertmanager.yml
fi

node - <<'NODE'
const fs = require('node:fs');
const path = require('node:path');

function visit(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) visit(file);
    else if (entry.name.endsWith('.json')) {
      JSON.parse(fs.readFileSync(file, 'utf8'));
      console.log(`valid json: ${file}`);
    }
  }
}

visit('infra/observability/grafana');
NODE

echo "Monitoring configuration validation passed"
