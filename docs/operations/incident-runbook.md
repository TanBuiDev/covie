# Pilot incident runbook

## First actions

1. Declare an incident owner and start a timestamped operator log.
2. Preserve logs/metrics and the current/previous release manifests; do not paste secrets into the incident channel.
3. Classify the failure: application release, PostgreSQL/data, Redis/rate-limit degradation, object storage, TLS/DNS/ingress or host capacity.
4. Prefer the smallest reversible action that preserves evidence and data integrity.

## Application release failure

If health/smoke fails after a release and the previous app is schema-compatible, run `infra/deploy/rollback.sh`. Confirm homepage, `/api/v1/health`, catalog, auth rejection behavior and monitoring recovery. Database migrations remain in place.

## PostgreSQL/data integrity

Do not run ad-hoc destructive SQL or automatic down migrations. Restrict writes if needed, preserve the source database, select PITR/logical backup, restore into a new database, run `infra/backup/verify-backup.sh`, Flyway validation and business invariant checks, then make an explicit cutover decision. Keep the original database available for evidence until the incident is closed.

## Redis/rate limiting

Redis is not authoritative business state. Fail-closed groups may return controlled 503; fail-open groups such as public discovery may continue while emitting degradation metrics. Restore Redis availability/credentials and verify limiter metrics. Do not disable database authorization, idempotency or locking to compensate.

## Object storage

Do not mark media READY manually. Existing PostgreSQL-backed features should remain available while upload/head/complete paths fail in their controlled error path. Restore provider access, verify bucket policy/CORS/encryption and allow normal media verification/cleanup to resume.

## TLS/DNS/Nginx

If certificate/DNS ownership is uncertain, block release rather than weakening HTTPS or HSTS. Validate `nginx -t`, certificate expiry/chain, DNS target and security headers. API/Web/management ports must remain private.

## Capacity/outbox

Inspect host CPU/memory/disk, Hikari utilization, PostgreSQL connections, outbox oldest-due age and 5xx ratio. Do not blindly increase pools. Use query/load evidence to address the bottleneck.

## Closure

Record root cause, affected window, release digest, data verification result, rollback/restore actions, alerts that fired or failed to fire, follow-up owners and any security secret rotation that was required. A secret suspected of exposure must be rotated; do not suppress the scanner and keep using it.
