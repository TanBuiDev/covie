#!/usr/bin/env bash
set -euo pipefail

backup="${1:-}"
[[ -f "$backup" ]] || { echo "usage: $0 <backup.dump>" >&2; exit 2; }
checksum_file="$backup.sha256"
[[ -f "$checksum_file" ]] || { echo "Missing backup checksum" >&2; exit 1; }
expected="$(awk 'NR==1 {print $1}' "$checksum_file")"
actual="$(sha256sum "$backup" | awk '{print $1}')"
[[ "$expected" =~ ^[0-9a-f]{64}$ && "$expected" == "$actual" ]] || {
  echo "Backup checksum mismatch" >&2; exit 1;
}
command -v pg_restore >/dev/null 2>&1 || { echo "pg_restore is required to verify the backup archive" >&2; exit 1; }
pg_restore --list "$backup" >/dev/null
echo "Backup checksum and archive verified."
