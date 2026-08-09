#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$ROOT_DIR/packages/api-client/openapi/openapi.json"

cd "$ROOT_DIR/apps/api"
./mvnw -Dtest=OpenApiExportTest -Dcovie.openapi.output="$OUTPUT" test

cd "$ROOT_DIR"
pnpm --filter @covie/api-client generate
