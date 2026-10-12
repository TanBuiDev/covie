# SSR session refresh

User requirement: Access Token expiry must not log out a user with a valid Refresh Token. Preserve15-minute access/30-day refresh durations, HttpOnly cookies, rotation/revocation and CSRF.

Root cause: Server Components forward page cookies but refresh cookie has Path=/api/v1/auth and is not sent on page navigation; server reads currently do not refresh. Server Components cannot set browser cookies. Nginx routes all /api/v1 traffic to API by default.

- [x] Regression: SSR401 redirects to auth-path refresh bridge; preserve actual page/query through shared server client and authoritative page Proxy.
- [x] Node Route Handler under /api/v1/auth/ssr-refresh receives path-scoped refresh cookie, obtains CSRF then POSTs backend refresh, forwards Set-Cookie and verifies /me before returning to page. No raw tokens in body/URL/logs. Transient failures remain retryable; invalid/revoked sessions go to login; cross-site calls rejected; returnTo safe.
- [x] Page Proxy records authoritative incoming path only; server read middleware redirects401 to bridge, never writes cookies in Server Components. Browser refresh also uses the same bridge (client=1 JSON mode); concurrent requests coalesce in-flight exchanges within current single-web-process topology. No completed credential cache.
- [x] Exact Nginx route forwards bridge to web in both configs. Existing static Nginx/Compose verifier now checks both bridge upstreams and passes; actual Nginx process/reload not tested.
- [x] Cookie duration/path verified in real browser: access15m/refresh30d, HttpOnly, refresh Path=/api/v1/auth. Production/local JWT configuration loads existing PEM files; dev launcher reuses keys. Configuration reload test uses temporary test keys, not a restart of the recovered backend.
- [x] Focused FE80 tests and final full FE456 tests pass. Typecheck, changed-file lint, production build and real Chrome/Next/Spring/PostgreSQL F5 acceptance pass. Final backend7 suites21 tests pass including real browser acceptance and temporary-PEM configuration reload. Initial full FE run had2 intermittent AdSlot failures; final rerun passes without changing ads.
- [ ] Repository-wide lint remains blocked by12 errors/25 warnings in other UI files (unescaped quotes and render-time Date.now, plus unused imports). No lint disabling or unrelated UI repair in this task.

Additional proven fix: requestBrowserApi returned the original401 after successful refresh/retry. Regression failed401 instead200; now captures the final middleware response before parsing, keeping its body readable. SellerReputation server component used the client icon entry point; production build failed createContext. Import changed to SSR entry point, build passes.

Evidence: .local/ssr-final-focused-20261010.log, ssr-web-full-20261010.log, ssr-typecheck-20261010.log, ssr-changed-lint-20261010.log, ssr-build-20261010.log, ssr-final-auth-20261010.log, ssr-browser-runtime-20261010.log. Browser acceptance retains a correctly signed but expired JWT for first F5, expires the access cookie for second F5, verifies two rotations and revoked/expired refresh login. Uses an isolated database and redirects browser hydration requests to its actual backend, simulating Nginx routing.

Deployment: rebuild/restart web and reload the updated Nginx configuration together; API refresh/revocation policy unchanged. No production deployment or recovered data edits performed. The original checkpoint above used in-memory coordination; the authorized distributed follow-up below supersedes that concurrency design. Whole Backend audit remains open.

## Authorized distributed follow-up — 2026-10-10

- [x] Reproduce independent web instances sending two POSTs, Redis outage permitting rotation, invalid-token guards retained too long, and unsafe Redis configuration allowing refresh.
- [x] Replace the process Map with atomic Redis owner claim/result publication. Five-second encrypted sharing handles concurrent and late requests; 32-second bounded wait covers backend timeout budgets.
- [x] Retain non-reclaimable fingerprint guards for successful/ambiguous POSTs, release safe pre-POST/429 failures, preserve owner cookies if publication fails. Add persistent non-evicting Redis configuration and runtime configuration validation.
- [x] Reproduce and fix missing HttpOnly browser binding: bootstrap a 256-bit binding cookie and derive encryption from both RT/binding; another browser cannot retrieve cached successor credentials.
- [x] Reverify `/me` for every delivered success, preserve cookie paths/TTL/security, reduce cached Max-Age, retain backend CSRF/replay policy and avoid token logging.
- [x] Final full FE476 tests/111 files, typecheck/changed lint/build and real Chrome/dual Next/Spring/Postgres/Redis gate pass. Seven backend suites22 tests/0 failures/errors/skips, BUILD SUCCESS48.676s terminal15:19:47; logs `ssr-redis-web-full-20261010.log`, `ssr-redis-build-20261010.log`, `ssr-redis-real-auth-20261010.log`. Actual dual Next verifies exactly one rotation, same AT/RT, late replay delivery and logout before cached-result delivery; Chrome verifies both F5 expiry modes, invalid RT and HttpOnly binding. Initial dual-test assertion counted the CSRF cookie as an auth cookie; corrected only the assertion and reran all seven suites.
- [ ] Actual target deployment/web restart/Nginx reload, then live acceptance. Local Compose durability changes require Redis recreation; external production Redis must meet the documented policy first.

Architecture, recovery limits, Redis ACL/capacity and rollout are recorded in `docs/operations/ssr-refresh-coordination.md`. Guard loss during asynchronous Redis failover is not claimed solved; backend transactional recovery is required for that stronger guarantee.

Additional delivery evidence: three real Node processes and a >1.5s owner delay coalesce; a process crash after beginning rotation preserves the guard; cache expiry does not permit takeover; disposable Redis restart retains the spent marker and one POST counter. Nginx/Compose static checks and `git diff --check` pass. No recovered database/backend restart or production deployment occurred.

Runtime env verifier initially rejected Windows CRLF `prod` value; normalize CRLF/BOM during reads. LF, CRLF+BOM and checked-in production example now pass. This is a parser portability correction; required production fields and validation remain enforced. Disposable Redis/test processes cleaned up.

Production dependency audit is not clean: `pnpm audit --prod --json` reports9 findings (1 critical/3 high/4 moderate/1 low), in existing `next`, `sharp`, `source-map-js`; new Redis packages are not listed. Evidence `.local/ssr-redis-prod-audit-20261010.json`. Dependency remediation/release acceptance is separate from the verified refresh feature; do not claim production readiness.
