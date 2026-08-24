#!/usr/bin/env bash
set -euo pipefail
: "${COVIE_STORAGE_BUCKET:?COVIE_STORAGE_BUCKET is required}"
args=(--bucket "$COVIE_STORAGE_BUCKET")
[[ -z "${COVIE_STORAGE_ENDPOINT:-}" ]] || args+=(--endpoint-url "$COVIE_STORAGE_ENDPOINT")
aws s3api get-public-access-block "${args[@]}" >/dev/null
aws s3api get-bucket-encryption "${args[@]}" >/dev/null
versioning="$(aws s3api get-bucket-versioning "${args[@]}" --query Status --output text)"
[[ "$versioning" == "Enabled" ]] || { echo "Bucket versioning is not enabled" >&2; exit 1; }
aws s3api get-bucket-cors "${args[@]}" >/dev/null
aws s3api get-bucket-lifecycle-configuration "${args[@]}" >/dev/null
echo "S3 bucket hardening contract verified."
