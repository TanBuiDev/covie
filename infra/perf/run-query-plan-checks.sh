#!/usr/bin/env bash
set -euo pipefail

: "${COVIE_PERF_JDBC_URL:?Set COVIE_PERF_JDBC_URL for the production-like PostgreSQL target}"
: "${COVIE_PERF_QUERY_FILE:?Set COVIE_PERF_QUERY_FILE to a file containing exact hot SQL statements}"

[[ -f "$COVIE_PERF_QUERY_FILE" ]] || { echo "Missing query file: $COVIE_PERF_QUERY_FILE" >&2; exit 2; }

cd "$(dirname "$0")/../../apps/api"
COVIE_PERF_JDBC_URL="$COVIE_PERF_JDBC_URL" \
COVIE_PERF_DB_USER="${COVIE_PERF_DB_USER:-postgres}" \
COVIE_PERF_DB_PASSWORD="${COVIE_PERF_DB_PASSWORD:-}" \
COVIE_PERF_QUERY_FILE="$(cd ../.. && realpath "$COVIE_PERF_QUERY_FILE")" \
./mvnw -Dtest=HotQueryPlanIntegrationTest test
