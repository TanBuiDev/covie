#!/usr/bin/env bash
set -euo pipefail

tracked_secret_pattern='infra/secrets|jwt-private|(^|/)\.env($|\.)|\.(pem|key|p12|pfx|jks|keystore)$'

content_secret_pattern='^[[:space:]]*GOOGLE_OAUTH_CLIENT_SECRET=[^[:space:]]+|^[[:space:]]*-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'

check_tracked_paths() {
  set +e
  git ls-files | grep -vE '(^|/)\.env\.example$|\.env\.example$' | grep -E "$tracked_secret_pattern"
  local statuses=("${PIPESTATUS[@]}")
  set -e

  local scanner_status="${statuses[0]}"
  local grep_status="${statuses[2]}"
  # A downstream error can close the pipe and give upstream writers SIGPIPE.
  # Report the matcher/filter error rather than the consequential SIGPIPE.
  if [[ "$grep_status" -gt 1 ]]; then
    echo "Secret-hygiene path matcher failed with status $grep_status." >&2
    return "$grep_status"
  fi
  if [[ "${statuses[1]}" -gt 1 ]]; then
    echo "Secret-hygiene template filter failed." >&2
    return "${statuses[1]}"
  fi
  if [[ "$scanner_status" -ne 0 ]]; then
    echo "Secret-hygiene path scanner failed with status $scanner_status." >&2
    return "$scanner_status"
  fi
  if [[ "$grep_status" -eq 0 ]]; then
    echo "Tracked secret-like path detected." >&2
    return 1
  fi
  if [[ "$grep_status" -eq 1 ]]; then
    return 0
  fi
  echo "Secret-hygiene path matcher failed with status $grep_status." >&2
  return "$grep_status"
}

check_tracked_content() {
  set +e
  git grep -lE "$content_secret_pattern" -- ':!*.md' ':!*.env.example' ':!.env.example'
  local scanner_status=$?
  set -e

  if [[ "$scanner_status" -eq 0 ]]; then
    echo "Tracked secret-like content detected." >&2
    return 1
  fi
  if [[ "$scanner_status" -eq 1 ]]; then
    return 0
  fi
  echo "Secret-hygiene content scanner failed with status $scanner_status." >&2
  return "$scanner_status"
}

check_tracked_paths
check_tracked_content
