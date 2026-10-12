# SSR refresh coordination

The web refresh bridge at `/api/v1/auth/ssr-refresh` coordinates both browser and SSR refreshes across web processes. Access Token remains 15 minutes; the backend session/Refresh Token expires at the existing absolute 30-day boundary. No Admin configuration screen or backend API change is introduced.

## Protocol

1. The bridge rejects cross-site requests, validates `returnTo`, and receives the path-scoped Refresh Token.
2. When absent, it initializes a random 256-bit `covie_refresh_binding` cookie, HttpOnly/SameSite=Lax, Path=/api/v1/auth, Secure for HTTPS, and redirects to itself. Fetch follows this same-origin redirect automatically. This cookie is not an authentication credential; it binds shared refresh results to the browser that requested them.
3. A valid Access Token skips rotation. A 401 uses Redis coordination keyed by SHA-256 of the old Refresh Token. All replicas use the same Redis endpoint/database/key namespace.
4. An atomic Lua claim creates a uniquely owned guard. The owner obtains backend CSRF and attempts one POST refresh. Other requests poll every 75 ms, at most 32 seconds. They never take over an existing guard.
5. After POST, the result is published atomically with the spent guard. Results live for five seconds, including late requests after the owner completes. Each response forwards the same credentials while retaining its own return destination. Cached delivery subtracts elapsed time from cookie Max-Age.
6. Results use AES-256-GCM with a fresh IV, authenticated key identity, and HKDF derived from both the old high-entropy RT and the HttpOnly browser binding. Redis receives neither raw RT, binding, nor plaintext cookies. A different browser binding cannot decrypt the result. A holder of the old RT alone does not gain access to the successor from this cache.
7. Every successful result is checked against backend `/me` before forwarding cookies. Logout/session revocation during the result window therefore cannot turn a revoked session into a successful refresh.

## Failure behavior

- Redis missing, unreachable, unsafe configuration, invalid ciphertext, expired result, or orphaned guard: 503, no automatic backend rotation, no clearing of existing cookies and no forced login redirect. Temporary responses include Retry-After where a result is available.
- CSRF failure before POST: release only the owner's guard; retry is safe.
- Explicit backend 429: rate limiting is before `RefreshRotationTransaction`, so release the guard and allow retry. Other failures after POST are ambiguous and never retried with the old RT.
- Owner dies or loses the POST response: retain the guard. The backend may already have consumed the token. If the successor cannot be delivered, starting a new login session is the recovery; a Redis lock cannot reconstruct a lost backend response. This preserves safety without pretending to provide automatic recovery for every crash.
- Redis publication fails after a successful POST: still deliver the owner's rotated cookies. Followers retain their existing cookies and receive a temporary failure.
- Backend 401: normal login behavior and clearing AT/RT. Invalid-token guards expire after five seconds; they do not occupy Redis for 30 days.

Successful/ambiguous guards contain only the RT fingerprint key and owner/timestamp or `spent`; they last 30 days plus 60 seconds. This bounds the lifetime while covering the maximum current RT lifetime. If backend session TTL is increased, increase `REFRESH_GUARD_SECONDS` and review this policy together. Monitor Redis capacity: one small guard per rotation accumulates until expiry; do not apply eviction to this namespace.

Backend refresh rotation, replay detection/revocation, JWT keys, CSRF rules and DTOs remain unchanged. Five-second result sharing for the same browser is intentional coordination; after that window the old token cannot produce credentials through the bridge. Direct backend replay continues to follow backend revocation policy.

## Redis requirements and rollout

`REDIS_URL` is server-only and required for expired-session refresh. There is no RAM fallback. `pnpm dev` inherits it from root `.env.local`; the example already sets `redis://localhost:6379`. Production web inherits the shared deployment env file.

Before the first coordination command on each connection, the client checks:

```text
appendonly yes
appendfsync always
maxmemory-policy noeviction
```

Redis ACL must allow `CONFIG GET` for these settings, GET, SET, DEL, PEXPIRE and EVAL for the refresh namespace. Denying the configuration check fails closed. The client does not log Redis URL/errors, queues no offline commands, bounds connection/command timeouts to one second, and does not automatically reconnect/replay ambiguous writes.

Keep replica clocks synchronized for the bounded wait and cached-cookie lifetime checks. Failed connection attempts may be tried on a later request; pending Redis commands and old RT rotations are never blindly replayed.

Local Compose now mounts named `covie_redis` at `/data` with these settings. Apply it by recreating the Redis service through the normal development launcher/Compose procedure; changing the file alone does not change an already-running container. Keep the volume during ordinary stops and restarts.

For production, configure the existing Redis endpoint with durable storage and sufficient capacity before replacing web replicas. Back up/preserve guards through maintenance. Do not flush the namespace or roll back to a stale Redis snapshot while sessions using it remain active. Asynchronous replica failover/data loss can still lose guards: this implementation does not claim exactly-once execution across loss of authoritative Redis state. That stronger guarantee requires backend transaction-level idempotent refresh/result recovery.

Build/restart web together with the existing Nginx bridge route. Both Nginx configs route the exact bridge to web, disable cache there and allow a 60-second upstream read timeout. The general `/api/v1/` route stays on backend. No production target/reload is implied by a local code/build gate.

## Verification

Normal unit tests use a memory test double behind the production coordinator. The opt-in Redis suite uses a disposable Redis endpoint and covers independent module instances, three real Node processes, rotation slower than 1.5 seconds, late requests, expired results, owner crashes after POST, owner-only release/publish, invalid-token guard retention, revoked sessions, publication failures, unsafe Redis configuration, and browser-binding confidentiality.

```powershell
$env:COVIE_REFRESH_TEST_REDIS_URL = 'redis://127.0.0.1:<disposable-port>'
pnpm --filter @covie/web exec vitest run tests/auth
pnpm web:typecheck
pnpm web:build
```

Do not point that test suite at project/production Redis: it changes Redis configuration in one test and writes synthetic guard/counter keys. Use only a separate disposable container.

`SsrRefreshBrowserIntegrationTest` is opt-in with `-Dcovie.test.ssr-browser=true`. It starts separate Testcontainers Redis and PostgreSQL, two built Next servers for real backend concurrency/late-request/revocation checks, and Chrome for F5 with retained expired JWT or dropped AT cookie. The recovered project database is not used. Browser hydration routing simulates Nginx; an actual live Nginx reload remains a deployment acceptance step.

2026-10-10 evidence: full web476 tests/111 files, typecheck, changed-file lint and production build pass; seven real auth backend suites22 tests/0 failures/errors/skips pass in48.676s (terminal15:19:47). A disposable Redis restart retains the spent guard and does not allow a second rotation. Logs are under `.local/ssr-redis-*`. The feature is locally verified; the target runtime has not been restarted/deployed. Production dependency audit separately reports9 existing Next/sharp/source-map-js findings; remediation is needed before claiming release readiness.

The deployment environment verifier also accepts Windows CRLF/BOM env files after an observed false rejection of `SPRING_PROFILES_ACTIVE=prod`; both LF and CRLF+BOM examples now pass. The disposable Redis and all test application processes were cleaned up. Existing project services/data were not started or changed.

## Local SSR rollout — 2026-10-11
User selected both local and server deployment. Started existing managed scripts/dev.ps1 session (exec 67478); API localhost:8081 actuator health UP, web localhost:3000 HTTP 200, unauthenticated /api/v1/auth/ssr-refresh?returnTo=%2F HTTP 303 /login with private,no-store. Existing Redis recreated through normal Compose, CONFIG GET verified appendonly=yes, appendfsync=always, maxmemory-policy=noeviction. Restored PostgreSQL/MinIO volumes retained. This is local development runtime rollout, not a production build or live expired-session acceptance. Prior isolated real-browser expiry tests remain the expiry evidence. Server deployment awaits host/domain, staging or production, and access/pipeline details requested asynchronously; no remote deployment performed. Managed launcher remains running; pnpm dev:stop stops its services while preserving volumes.

Pilot coverage/cursor gate: actual PostgreSQL Need pagination reproduced duplicate next-page IDs from real rank compared with double cursor. JdbcNeedDiscoveryRepository now casts cursor rank to real for all range/equality branches. Opt-in PilotFixtureIntegrationTest verifies disjoint Need and catalog pages, Product delivery radius and area coverage. Fresh PilotFixtureIntegrationTest + HotQueryPlanValidationTest: 5 tests, zero failures/errors/skips, BUILD SUCCESS 33.733s at 13:55:50+07; .local/backend-pilot-coverage-cursor-green-20261011.log. RED log .local/backend-pilot-coverage-cursor-20261011.log retains actual overlap failure. An absent NeedDiscoveryServiceIntegrationTest selector in that command contributed no tests; no claim of that suite passing. Full 791-test gate predates this production rank fix. Whole backend completion remains open; add default-running regression and rerun broad checks.

## Ads membership revocation + deployment target clarification — 2026-10-11
User confirms no domain or VPS yet. No remote target exists: do not request credentials repeatedly or claim server rollout; local managed SSR runtime previously applied. Remote production acceptance/load stays CONFIG_REQUIRED, independent BE audit continues.
Extended real JWT Ads case: outsider GET merchant Ads404; revoke selected persisted OWNERmembership, same existingJWT GET/create404; valid other-merchant membership stillGET200. MerchantAccessPolicy intentionally hides absent/revoked membership with404 RESOURCE_NOT_FOUND. Initial16-test run failed incorrect403 expectation only; corrected test to existing404contract, no production change. Fresh fourAds suites16tests0fail/errors/skips BUILD SUCCESS40.856s terminal14:23:54+07 .local/backend-ads-revoked-membership-green-20261011.log session85013exit0. Initial .local/backend-ads-revoked-membership-20261011.log session79064exit1. No recovered test writes. WholeBE ACTIVE; next publicAds serializeddelivery/telemetry actualHTTP andremainingdomains/runtime/provider; noVPS notblocker for independentBEprogress.
