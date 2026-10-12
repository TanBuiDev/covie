#!/usr/bin/env bash
set -euo pipefail

file="${1:-}"
if [[ "$file" == "--example" ]]; then
  file="${2:-}"
  example_mode=true
else
  example_mode=false
fi
[[ -n "$file" && -f "$file" ]] || { echo "usage: $0 [--example] <env-file>" >&2; exit 2; }

declare -A cfg=()
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  line="${line#$'\xef\xbb\xbf'}"
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  name="${line%%=*}"
  value="${line#*=}"
  [[ "$name" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "Invalid variable name in env file" >&2; exit 1; }
  cfg["$name"]="$value"
done < "$file"

required=(SPRING_PROFILES_ACTIVE COVIE_PUBLIC_URL DB_URL DB_USERNAME DB_PASSWORD REDIS_URL COVIE_AUTH_ISSUER COVIE_COOKIE_SECURE COVIE_JWT_KEY_ID COVIE_JWT_PRIVATE_KEY_PATH COVIE_JWT_PUBLIC_KEY_PATH GOOGLE_OAUTH_CLIENT_ID GOOGLE_OAUTH_CLIENT_SECRET GOOGLE_OAUTH_REDIRECT_URI COVIE_STORAGE_BUCKET COVIE_STORAGE_REGION COVIE_MEDIA_PUBLIC_BASE_URL COVIE_RATE_LIMIT_HMAC_KEY)
for name in "${required[@]}"; do
  if [[ -z "${cfg[$name]+x}" ]]; then
    echo "Missing variable: $name" >&2
    exit 1
  fi
  if [[ "$example_mode" != true && -z "${cfg[$name]}" ]]; then
    echo "Blank variable: $name" >&2
    exit 1
  fi
done

[[ "${cfg[SPRING_PROFILES_ACTIVE]}" == "prod" ]] || { echo "Invalid variable: SPRING_PROFILES_ACTIVE" >&2; exit 1; }
[[ "${cfg[COVIE_PUBLIC_URL]}" == https://* ]] || { echo "Invalid variable: COVIE_PUBLIC_URL" >&2; exit 1; }
[[ "${cfg[COVIE_AUTH_ISSUER]}" == https://* ]] || { echo "Invalid variable: COVIE_AUTH_ISSUER" >&2; exit 1; }
[[ "${cfg[GOOGLE_OAUTH_REDIRECT_URI]}" == https://*/api/v1/auth/google/callback/google ]] || { echo "Invalid variable: GOOGLE_OAUTH_REDIRECT_URI" >&2; exit 1; }
[[ "${cfg[COVIE_COOKIE_SECURE]}" == "true" ]] || { echo "Invalid variable: COVIE_COOKIE_SECURE" >&2; exit 1; }
[[ "${cfg[COVIE_E2E_SUPPORT_ENABLED]:-false}" == "false" ]] || { echo "Invalid variable: COVIE_E2E_SUPPORT_ENABLED" >&2; exit 1; }

if [[ "$example_mode" != true ]]; then
  key="${cfg[COVIE_RATE_LIMIT_HMAC_KEY]}"
  if [[ "$key" == base64:* ]]; then
    encoded="${key#base64:}"
    [[ "$encoded" =~ ^[A-Za-z0-9+/]*={0,2}$ ]] || { echo "Invalid variable: COVIE_RATE_LIMIT_HMAC_KEY" >&2; exit 1; }
    # Java's Basic Base64 decoder also accepts unpadded input.
    if [[ "$encoded" != *=* ]]; then
      case $((${#encoded} % 4)) in
        2) encoded+='==' ;;
        3) encoded+='=' ;;
      esac
    fi
    key_bytes="$(printf '%s' "$encoded" | base64 --decode 2>/dev/null | wc -c)" || { echo "Invalid variable: COVIE_RATE_LIMIT_HMAC_KEY" >&2; exit 1; }
  else
    key_bytes="$(printf '%s' "$key" | wc -c)"
  fi
  (( key_bytes >= 32 )) || { echo "Invalid variable: COVIE_RATE_LIMIT_HMAC_KEY" >&2; exit 1; }
  for name in COVIE_JWT_PRIVATE_KEY_PATH COVIE_JWT_PUBLIC_KEY_PATH; do
    path="${cfg[$name]}"
    if [[ "$path" == /run/covie-secrets/* ]]; then
      relative="${path#/run/covie-secrets/}"
      [[ "/$relative/" != */../* ]] || { echo "Invalid variable: $name" >&2; exit 1; }
      path="${COVIE_JWT_SECRET_DIR:-/etc/covie/secrets}/$relative"
    fi
    [[ -r "$path" ]] || { echo "Unreadable file configured by: $name" >&2; exit 1; }
  done
fi

echo "Runtime environment contract OK."
