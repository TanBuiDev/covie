# Task 15 report — authentication security, transaction and IDOR matrix

## Coverage mapping

| Brief area | Evidence |
| --- | --- |
| First Google registration, identity/role/session/JWT | `AuthAcceptanceTest.firstGoogleRegistrationCreatesOneActiveUserBothIdentitiesRoleSessionAndUsableJwt` |
| Returning Google and email sign-in | `returningGoogleAndEmailLoginsResolveTheOriginalUserWithoutDuplicateIdentities` |
| Google recovery | `GoogleRecoveryRevokesOldSessionsRejectsOldPasswordAndIssuesUsableNewSession` |
| Refresh rotation, fixed session expiry, reuse and devices | `refreshRotatesWithoutExtendingExpiryAndReuseRevokesOnlyThatSessionFamily`; `revokeOneAndRevokeOthersPreserveTheRequestedCurrentSession` |
| Real PostgreSQL refresh contention | `simultaneousUsesOfOneRefreshTokenSerializeOnPostgresAndDetectReuse` |
| Onboarding race and cleanup | `concurrentOnboardingTicketsForOneGoogleIdentityCommitExactlyOneCompleteUser` |
| Cookie CSRF, Bearer bypass, GET, lifecycle invalidation | `AuthCookieCsrfAcceptanceTest` |
| Session/address/contact IDOR and status invalidation | `AuthIdorAcceptanceTest` |
| Audit secret safety | `AuthAuditSafetyTest` |
| Direct DB constraints, token hash/raw value, action single-use | `AuthDatabaseInvariantTest` |

## RED/GREEN evidence

The added concurrent-refresh acceptance test initially returned two successful refreshes. The first transaction read the token before acquiring the lock, leaving the stale entity in its persistence context after waiting on the session lock. The fix locks by token hash before reading it, which makes the second use observe `used_at` and revoke the family. The real PostgreSQL concurrency test is green.

The MVC tests initially returned 401 for valid JWTs. `ActiveSessionFilter` called the modifying `touchLastUsedIfBefore` query outside a transaction and caught the resulting exception as an invalid session. A transactional `SessionManagementService.requireActiveSessionForUser` now validates and touches within one transaction; IDOR/status tests are green.

The MVC CSRF RED test showed a cookie-authenticated POST with missing or bad CSRF reached `UserAddressController` with HTTP 200. The unsafe/no-Bearer matcher branch was applicable, but the configured framework CSRF flow did not enforce it in this resource-server/cookie chain. `BrowserCookieCsrfFilter` is installed before Bearer authentication and validates the configured cookie/header for requests carrying an auth cookie. Missing/bad tokens now return 403, a fetched valid token succeeds, and Bearer succeeds without CSRF.

## Commands and results

Docker diagnosis: `docker version` confirmed Docker Desktop server 29.5.2 and `docker context inspect desktop-linux` reported `npipe:////./pipe/dockerDesktopLinuxEngine`. Testcontainers was pinned to the legacy npipe strategy in `%USERPROFILE%/.testcontainers.properties`; process-local `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` made Ryuk and `postgis/postgis:18-3.6` start.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw "-Dtest=AuthAcceptanceTest,AuthCookieCsrfAcceptanceTest,AuthIdorAcceptanceTest,AuthAuditSafetyTest,AuthDatabaseInvariantTest" test
```

Result: **14 tests, 0 failures, 0 errors** (fresh Testcontainers PostgreSQL run; 20.799 s).

The brief's literal `-Dtest='vn.covie.auth.acceptance.*'` is not a Surefire pattern that selects these classes and exits with “No tests matching pattern”; the explicit class selection above is the executable equivalent.

## Changed files

- New acceptance suites under `apps/api/src/test/java/vn/covie/auth/acceptance/`.
- Refresh locking: `AuthRefreshTokenRepository`, `RefreshRotationTransaction`.
- Session validation/touch transaction: `SessionManagementService`, `ActiveSessionFilter`.
- Browser-cookie CSRF enforcement: `BrowserCookieCsrfFilter`, `AuthSecurityConfiguration`.

## Concerns / follow-up

The suite is focused on the contractual security paths and production defects it exposed. The CSRF test covers login invalidation plus valid/missing/invalid/Bearer/GET behavior; endpoint-by-endpoint lifecycle checks for refresh, onboarding, reset and logout, and stale-cookie public-route variants remain useful expansion cases. The test command needs the Docker Desktop npipe override in this environment.
