#!/usr/bin/env bash
set -euo pipefail
: "${COVIE_STORAGE_BUCKET:?COVIE_STORAGE_BUCKET is required}"
args=(--bucket "$COVIE_STORAGE_BUCKET")
[[ -z "${COVIE_STORAGE_ENDPOINT:-}" ]] || args+=(--endpoint-url "$COVIE_STORAGE_ENDPOINT")
public_block="$(aws s3api get-public-access-block "${args[@]}" \
  --query 'PublicAccessBlockConfiguration.[BlockPublicAcls,IgnorePublicAcls,BlockPublicPolicy,RestrictPublicBuckets]' --output text)"
read -r block_acls ignore_acls block_policy restrict_buckets extra <<< "$public_block"
[[ "$block_acls" == True && "$ignore_acls" == True && "$block_policy" == True && "$restrict_buckets" == True \
  && -z "$extra" && "$public_block" != *$'\n'* ]] || { echo "Bucket public access block is not fully enabled" >&2; exit 1; }
aws s3api get-bucket-encryption "${args[@]}" >/dev/null
versioning="$(aws s3api get-bucket-versioning "${args[@]}" --query Status --output text)"
[[ "$versioning" == "Enabled" ]] || { echo "Bucket versioning is not enabled" >&2; exit 1; }
aws s3api get-bucket-cors "${args[@]}" >/dev/null
aws s3api get-bucket-lifecycle-configuration "${args[@]}" >/dev/null
echo "S3 bucket hardening contract verified."
