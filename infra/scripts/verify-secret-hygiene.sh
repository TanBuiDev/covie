#!/usr/bin/env bash
set -euo pipefail

tracked_secret_pattern='infra/'
tracked_secret_pattern+='secrets|jwt-'
tracked_secret_pattern+='private|\.env\.'
tracked_secret_pattern+='local'

content_secret_pattern='GOOGLE_OAUTH_CLIENT_SECRET'
content_secret_pattern+='=.+|BEGIN (RSA '
content_secret_pattern+=')?PRIVATE KEY'

check_tracked_paths() {
  set +e
  git ls-files | grep -E "$tracked_secret_pattern"
  local statuses=("${PIPESTATUS[@]}")
  set -e

  local scanner_status="${statuses[0]}"
  local grep_status="${statuses[1]}"
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
  git grep -nE "$content_secret_pattern" -- ':!*.md' ':!.env.example'
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
