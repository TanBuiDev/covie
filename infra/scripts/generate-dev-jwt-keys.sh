#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT_DIR/infra/secrets/dev"
mkdir -p "$OUT"

if [[ -e "$OUT/jwt-private.pem" || -e "$OUT/jwt-public.pem" ]]; then
  echo "dev JWT keys already exist; refusing to overwrite" >&2
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$OUT/jwt-private.pem"
openssl rsa -pubout -in "$OUT/jwt-private.pem" -out "$OUT/jwt-public.pem"
chmod 600 "$OUT/jwt-private.pem"
