#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

./infra/scripts/export-openapi.sh

git diff --exit-code -- \
  packages/api-client/openapi/openapi.json \
  packages/api-client/src/generated/schema.ts
