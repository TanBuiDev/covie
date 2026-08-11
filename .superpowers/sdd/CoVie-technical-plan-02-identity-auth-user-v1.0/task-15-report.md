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

## Fix round 1 (review follow-up)

### Root cause and changes

The first refresh fix took the refresh token row before the session. Logout, session revocation and password recovery take the session before that session's refresh-token family. This inconsistent order could deadlock and, when refresh waited after materializing the token entity, could retain stale ORM state. Refresh now performs scalar token-id/session-id lookups (which do not materialize an entity), locks the session first, then locks the token. Session revocation/recovery already uses that order.

`RequestIdFilter` accepted 128-character values although `audit_logs.request_id` is `varchar(100)`. The validation ceiling is now 100. `ActiveSessionFilter` now converts only expected `ApiException` and malformed UUID errors to an authentication response; unexpected runtime failures are allowed to reach normal 5xx handling.

Concurrency futures in the acceptance suite now have deterministic 10-second timeouts.

### Fix-round verification

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw -Dtest=AuthAcceptanceTest test
```

Result: **7 tests, 0 failures, 0 errors**, including real PostgreSQL concurrent refresh and onboarding paths.

## Fix round 1 completion — expanded matrix

### Added coverage and evidence

| Finding / contract | Executable evidence |
| --- | --- |
| Global session-then-token order remains deadlock-free | `refreshRacingLogoutOrGoogleResetCompletesAndLeavesTheOriginalFamilyRevoked` races refresh against logout and Google password reset, bounds both futures to ten seconds, and proves the original session and every family token are revoked. |
| Reuse vs successor refresh | `oldReuseRacingSuccessorRefreshCommitsReuseAuditAndRevokesTheWholeFamily` races old R1 reuse against successor R2 refresh, requires reuse detection, a single persisted audit event, and full-family revocation. |
| Maximum accepted request id | `refreshReuseWithMaximumAcceptedRequestIdPersistsAuditAndFamilyRevocation` uses exactly 100 characters and proves the reuse audit persists and revocation commits. |
| Operational session validation failures | `ActiveSessionFilterFailureTest.operationalSessionValidationFailurePropagatesInsteadOfBecomingUnauthorized` injects `DataAccessResourceFailureException`, proves it escapes the filter (for normal 5xx handling), and verifies the 401 entry point is not invoked. |
| CSRF lifecycle and stale cookie boundary | `csrfIsInvalidatedThenRegeneratedAfterOnboardingRefreshResetAndLogout` proves clear-cookie plus a different next `/auth/csrf` token for onboarding, refresh, Google reset, and logout. `staleAccessCookieDoesNotBlockPublicAuthEndpointsOrGoogleRecoveryStart` sends an invalid `covie_at` through login, refresh, logout, and Google reset-start, with valid CSRF where applicable. Cookie names now come from `AuthProperties`. |
| JWT/session end-to-end boundary | `logoutAndGoogleRecoveryRejectThePreviouslyUsableAccessJwt` verifies the current-session logout and recovery-revoked access JWT both fail on the next `/me` request. |
| Audit/log secret safety | `AuthAuditSafetyTest` now attaches a Logback `ListAppender` during login/refresh/reset and scans captured application log messages together with audit JSON for distinctive passwords, JWTs, refresh/action tokens, CSRF value, and OAuth code. |
| DB invariants | Explicit `saveAndFlush` duplicate refresh hash insertion fails. The same onboarding user is asserted to have GOOGLE metadata email equal to its EMAIL identity email. |

### RED/GREEN notes

The above are acceptance-characterization additions against the already fixed code. The focused test cycle was GREEN without a further production edit: the prior refresh/session order, 100-character request-id validation, and narrowed `ActiveSessionFilter` exception handling satisfy the added cases. The filter-failure test would regress to an entry-point 401 if the former broad `RuntimeException` catch were restored.

### Final verification

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw '-Dtest=ActiveSessionFilterFailureTest' test
.\mvnw '-Dtest=AuthAcceptanceTest,AuthCookieCsrfAcceptanceTest,AuthIdorAcceptanceTest,AuthAuditSafetyTest,AuthDatabaseInvariantTest' test
```

Final rerun after the audit-log and stale-cookie recovery extensions: the combined command completed **21 tests, 0 failures, 0 errors, 27.748 s** on a fresh Docker/Testcontainers PostgreSQL process (the five requested acceptance classes account for 20; the direct operational-failure filter test accounts for one).

### Files changed in this completion pass

- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAcceptanceTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthCookieCsrfAcceptanceTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthIdorAcceptanceTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAuditSafetyTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthDatabaseInvariantTest.java`

## Fix round 3 — selector safety scope

The first selector bridge set `failIfNoSpecifiedTests=false` in the global Surefire configuration. That made the mandated literal work, but it would also have allowed an accidental ordinary `-Dtest` typo to pass with zero selected tests. The tolerance is now configured only on the `task-15-plan-literal-selector` profile, which activates solely for the exact literal `test=vn.covie.auth.acceptance.*`. The normal/default Surefire configuration retains its failure-on-no-match behavior.

### RED/GREEN verification

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw '-Dtest=DefinitelyMissingTask15SelectorTest' test
```

RED (expected): **BUILD FAILURE** with `No tests matching pattern "DefinitelyMissingTask15SelectorTest" were executed!`.

```powershell
.\mvnw "-Dtest=vn.covie.auth.acceptance.*" test
```

GREEN: the property-activated bridge ran the five acceptance classes: **20 tests, 0 failures, 0 errors, 22.355 s** on fresh Testcontainers PostgreSQL.

```powershell
.\mvnw test
```

GREEN: normal suite **84 tests across 43 reports, 0 failures, 0 errors**. (The Maven output was long; the Surefire XML report totals were checked after execution.)
- `apps/api/src/test/java/vn/covie/auth/security/ActiveSessionFilterFailureTest.java`

### Remaining concern

The acceptance suite exercises real PostgreSQL and the installed HTTP filter chain; the operational database-error case is deliberately a direct filter test because terminating the Testcontainers database inside an HTTP test would make the test nondeterministic and contaminate the shared context. It proves the required discriminator: operational error propagation rather than a fabricated 401.

## Fix round 2 — reviewer follow-up

### Added/strengthened evidence

- The first-registration acceptance path now starts at the real `GoogleOAuthSuccessHandler` with an `OidcUser` principal, extracts its onboarding action cookie, posts `/auth/onboarding/set-password` through MockMvc, extracts the resulting access cookie, and proves the exact returned JWT authenticates `/api/v1/me` as the new actor. It no longer calls `GoogleAuthService` directly then mints an unrelated JWT.
- Maximum request-id reuse coverage now sends a 100-character `X-Request-Id` through MockMvc `/auth/refresh`, checks the returned response header and nested error code, then verifies the committed reuse audit record has that exact ID and the family is revoked.
- Audit safety now uses real HTTP login, cookie refresh, deliberate refresh reuse, and Google password reset; it sends a distinctive double-submit CSRF fixture through cookie-auth refresh and a distinctive OAuth `code` through the OAuth failure-handler boundary. It requires the four expected audit actions before scanning audit snapshots plus captured Logback messages for every raw password, initial/rotated/reset access JWT, initial/rotated/reset raw refresh, reset action ticket, CSRF fixture, and OAuth code.
- The database character-column probe now uses substring containment (`position(... in ...)`) instead of exact equality, so a token embedded in JSON/text is detected.
- The literal Task 15 command remains unchanged. `pom.xml` activates a narrowly scoped Surefire execution only when `test=vn.covie.auth.acceptance.*`; the default execution tolerates that legacy dotted selector's zero direct matches and the activated execution runs the explicit acceptance class list. Other selectors continue to use the normal execution.

### RED/GREEN evidence

RED: before the selector bridge, the literal plan command failed immediately with `No tests matching pattern "vn.covie.auth.acceptance.*" were executed!`.

GREEN: after the profile-scoped Surefire bridge, the same literal command invoked `acceptance-plan-selector` and completed the five acceptance classes successfully. During focused request-id test development, the response was correctly 401 with the expected nested envelope code, but the new assertion incorrectly used `$.code`; it was corrected to `$.error.code` with no production change.

### Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw "-Dtest=vn.covie.auth.acceptance.*" test
```

Result: **20 tests, 0 failures, 0 errors, 28.197 s** on fresh Testcontainers PostgreSQL.

### Changed files (round 2)

- `apps/api/pom.xml`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAcceptanceTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAuditSafetyTest.java`
- `apps/api/src/test/java/vn/covie/auth/acceptance/AuthDatabaseInvariantTest.java`
