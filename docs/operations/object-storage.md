# Production object storage

The origin bucket is private: no anonymous list/read, no browser credentials, backend-generated keys only, versioning enabled and encryption at rest. The application principal should receive only the object operations required by CoVie media (`PutObject`, `HeadObject`, bounded `GetObject`, `DeleteObject`) for the configured bucket/prefix; bucket-administration credentials are an operator-only concern.

Apply `infra/storage/cors.json` and `lifecycle.json` with the provider's administrative identity. `apply-s3-hardening.sh` configures AWS-compatible public-access block, AES-256 server-side encryption, versioning, CORS and incomplete-multipart cleanup. Some S3-compatible providers may require API syntax translation, but permissions must not be broadened. The lifecycle file never TTL-deletes current READY media; application orphan semantics remain database-authoritative.

Production fails startup when bucket/region/public delivery URL are missing or unsafe, when a configured endpoint is local/non-HTTPS, when presign TTL exceeds ten minutes, or when max image size exceeds 10 MiB. `COVIE_MEDIA_PUBLIC_BASE_URL` must be HTTPS. Path-style addressing stays disabled unless the selected provider requires it.

If object storage is unavailable, existing database-backed features remain available. Upload/head/complete operations fail in their existing controlled path; operators must not manually mark media READY. S3 outage alone must not make whole-API readiness fail. Recovery is to restore provider access and let normal verification/cleanup semantics resume.

Before launch run `verify-s3-hardening.sh`, then exercise a generated-key presigned PUT with the signed content type, prove a mismatched content type/unsigned key fails, complete the upload through the API, and confirm the READY URL uses the configured public base and browser responses/logs contain no storage credential or signed query secret. These provider-backed checks require staging credentials and remain part of Task 19 rehearsal evidence.
