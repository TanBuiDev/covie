#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

command -v docker >/dev/null 2>&1 || { echo "docker is required to validate pinned monitoring configs" >&2; exit 2; }
compose="$root/infra/compose/compose.observability.yml"
image_for() {
  awk -v service="$1" '
    $0 == "  " service ":" { selected = 1; next }
    selected && /^  [^ ]/ { selected = 0 }
    selected && $1 == "image:" { gsub(/\r$/, "", $2); print $2; exit }
  ' "$compose"
}
prometheus_image="$(image_for prometheus)"
alertmanager_image="$(image_for alertmanager)"
[[ -n "$prometheus_image" && -n "$alertmanager_image" ]] || { echo "Monitoring images missing from Compose" >&2; exit 2; }
mount_root="$root"
if command -v cygpath >/dev/null 2>&1; then
  mount_root="$(cygpath -m "$root")"
fi
docker_tool() {
  MSYS_NO_PATHCONV=1 docker "$@"
}

docker_tool run --rm \
  --entrypoint promtool \
  -v "$mount_root/infra/observability/prometheus:/etc/prometheus:ro" \
  "$prometheus_image" \
  check config /etc/prometheus/prometheus.yml

docker_tool run --rm \
  --entrypoint promtool \
  -v "$mount_root/infra/observability/prometheus:/etc/prometheus:ro" \
  "$prometheus_image" \
  check rules /etc/prometheus/rules/covie.rules.yml

for test_file in infra/observability/prometheus/tests/*.test.yml; do
  [[ -f "$test_file" ]] || continue
  docker_tool run --rm \
    --entrypoint promtool \
    -v "$mount_root/infra/observability/prometheus:/etc/prometheus:ro" \
    "$prometheus_image" \
    test rules "/etc/prometheus/tests/$(basename "$test_file")"
done

if [[ -f infra/observability/alertmanager/alertmanager.yml ]]; then
  docker_tool run --rm \
    --entrypoint amtool \
    -v "$mount_root/infra/observability/alertmanager:/etc/covie-alertmanager:ro" \
    "$alertmanager_image" \
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
