#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

command -v docker >/dev/null 2>&1 || { echo "docker is required to validate pinned monitoring configs" >&2; exit 2; }

docker run --rm \
  -v "$root/infra/observability/prometheus:/etc/covie-prometheus:ro" \
  prom/prometheus:v3.11.3 \
  promtool check config /etc/covie-prometheus/prometheus.yml

docker run --rm \
  -v "$root/infra/observability/prometheus:/etc/covie-prometheus:ro" \
  prom/prometheus:v3.11.3 \
  promtool check rules /etc/covie-prometheus/rules/covie.rules.yml

if [[ -f infra/observability/alertmanager/alertmanager.yml ]]; then
  docker run --rm \
    -v "$root/infra/observability/alertmanager:/etc/covie-alertmanager:ro" \
    prom/alertmanager:v0.32.1 \
    amtool check-config /etc/covie-alertmanager/alertmanager.yml
fi

python3 - <<'PY'
import json
from pathlib import Path
for path in Path('infra/observability/grafana').rglob('*.json'):
    json.loads(path.read_text(encoding='utf-8'))
    print(f'valid json: {path}')
PY

echo "Monitoring configuration validation passed"
