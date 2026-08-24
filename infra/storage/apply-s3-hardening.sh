#!/usr/bin/env bash
set -euo pipefail
: "${COVIE_STORAGE_BUCKET:?COVIE_STORAGE_BUCKET is required}"
: "${COVIE_STORAGE_REGION:?COVIE_STORAGE_REGION is required}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
args=(--bucket "$COVIE_STORAGE_BUCKET")
[[ -z "${COVIE_STORAGE_ENDPOINT:-}" ]] || args+=(--endpoint-url "$COVIE_STORAGE_ENDPOINT")

aws s3api put-public-access-block "${args[@]}" --public-access-block-configuration 'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'
aws s3api put-bucket-encryption "${args[@]}" --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-bucket-versioning "${args[@]}" --versioning-configuration Status=Enabled
aws s3api put-bucket-cors "${args[@]}" --cors-configuration "file://$root/infra/storage/cors.json"
aws s3api put-bucket-lifecycle-configuration "${args[@]}" --lifecycle-configuration "file://$root/infra/storage/lifecycle.json"
echo "S3 hardening configuration applied to the configured bucket."
