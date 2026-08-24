#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

./infra/scripts/export-openapi.sh

node <<'EOF'
const fs = require('node:fs');
const path = require('node:path');

const openApiPath = path.join(process.cwd(), 'packages/api-client/openapi/openapi.json');
const document = JSON.parse(fs.readFileSync(openApiPath, 'utf8'));
const requiredPaths = [
  '/api/v1/me/merchants',
  '/api/v1/merchants/{merchantId}/locations',
  '/api/v1/media/uploads',
  '/api/v1/media/{mediaId}/complete',
  '/api/v1/merchants/{merchantId}/catalog-items',
  '/api/v1/catalog/items',
  '/api/v1/catalog/items/{itemId}'
];

const missing = requiredPaths.filter((requiredPath) => !document.paths?.[requiredPath]);
if (missing.length > 0) {
  console.error('Missing required OpenAPI paths:', missing.join(', '));
  process.exit(1);
}
EOF

git diff --exit-code -- \
  packages/api-client/openapi/openapi.json \
  packages/api-client/src/generated/schema.ts
