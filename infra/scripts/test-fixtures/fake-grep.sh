#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${FAKE_GREP_STATUS+x}" ]]; then
  exit "$FAKE_GREP_STATUS"
fi

PATH=/usr/bin:/bin grep "$@"
