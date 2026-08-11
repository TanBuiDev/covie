#!/usr/bin/env bash
set -euo pipefail

case "${FAKE_GIT_MODE:-}:${1:-}" in
  match:ls-files)
    printf '%s\n' 'infra/secrets-placeholder'
    ;;
  no-match:ls-files)
    printf '%s\n' 'apps/api/src/main/java/vn/covie/CovieApplication.java'
    ;;
  scanner-error:ls-files)
    exit 2
    ;;
  no-match:grep)
    exit 1
    ;;
  *)
    echo "Unexpected fake git invocation: ${FAKE_GIT_MODE:-}:${1:-}" >&2
    exit 2
    ;;
esac
