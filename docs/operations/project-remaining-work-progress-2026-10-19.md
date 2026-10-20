# Remaining-work execution — local evidence receipt

**Subsequent activation request2026-10-20:** Owner requested all four Billing controlsON on local, then supplied normal Admin credentials via Drive. All four are nowON: payment/release/reconciliation environment true, actual gauges1/1, webhook reconfirmed200/code00; GLOBAL enforcement PATCH201, effective/onboarding rereadtrue, history/audit and one PUBLISHED outbox verified. Temporary Admin logout204; credentials/cookies memory-only, invoice count0. This narrowly supersedes the OFF receipt below; it does not mark live settlement, Social/video or production accepted. See current acceptance index/private activation receipt for the latest state. The initial helper PATCH403 was rejected before mutation; fetching fresh CSRF immediately before the authorized mutation resolved the helper sequencing issue without weakening protection.

Recorded2026-10-20; approved plan/baseline dated2026-10-19. Base main `5ad071ffb9f3c2f9d7cfaf327c09726690dc3c1a` plus the uncommitted changes below. This is partial local execution, not whole-project or production acceptance. Current W01–W14 state is `project-acceptance-current-2026-10-19.md`; outstanding issues are `project-open-issues-2026-10-19.md`.

## Safety and scope

Latest owner AGENTS requires normal money/release/reconciliation/enforcement/Social flags OFF, no recovered-DB writes, real payment or deployment. Local payment/release environment flags were set false and the managed stack stopped; data volumes retained. No invoice, link, grant or transfer was created by this work. User-owned tunnel/bridge processes were not deleted. No new branch, commit, push, merge, migration edit or dependency-lock change was made. Existing dirty Web AGENTS/next-env files were preserved and excluded from candidate context.

## Confirmed source gap and minimal fix

Original technical Plan06 Task16 Step4 requires sanitized Need operation metadata. The baseline lacked this writer. Added `NeedOperationLogging` at the existing MVC boundary: fixed operation names, authenticated UUID actor, UUID resource context, sanitized request ID and HTTP outcome/error enum. No request text, address, coordinates, credentials, token or exception message is logged. No extra database/authentication reads, endpoint/DTO/business mutation or durable-event claim was added. Unknown identifiers stay null.

Tests use disposable fixtures, not recovered business data. Initial missing mock dependencies, an unauthenticated JWT test fixture and an invalid test enum were harness corrections, not product failures. The meaningful RED was the missing-log assertion (expected1, actual0). The real-MVC test explicitly installs the existing RequestIdFilter in its manually built MockMvc; this is not an application filter repair.

| Evidence | Actual result / limitation |
|---|---|
| `.local/need-operation-log-red-assertion.log` | Missing metadata writer assertion failed before implementation |
| `.local/need-operation-log-final-valid.log` |4unit PASS; privacy, conflict, failure outcome, Buyer private-field exclusion |
| `.local/need-operation-log-final-wiring.log` |5PASS:4unit plus issued-JWT/realMVC/ownedPostgres test |
| `.local/need-operation-log-http-regression.log` |62PASS/0skip in affected HTTP integration class;35.979s Maven |
| `.local/project-completion-api-native-probe.log` |1PASS/0skip; actual candidate image FFmpeg/ffprobe for MP4/WebM/invalid input;8.969s Maven. Probe test copies compiled probe classes, not full upload/browser acceptance |

These are separate scoped runs, not a new full-backend aggregate. Unchanged historical PASS work was not repeatedly rerun.

### Three original Plan05 race gaps closed locally

Searched the existing Order API/persistence/performance tests first; other actor pairs were present, but not these exact pairs. Added only the three named Task18 cases to `OrderCancellationControllerIntegrationTest`: confirm vs Buyer pending cancel; confirm vs Seller reject; Buyer receive vs automatic completion. Each runs3times on disposable Postgres. An explicit held order-row lock and pg_stat_activity lock-wait assertions establish overlap of both commands before release, not merely two sequential requests. Issued JWTs exercise real HTTP security for human actors; automatic completion calls the actual selected-order scheduler worker, not scheduler due-selection or grace-clock acceptance.

`.local/project-remaining-three-races-offline.log`:9PASS/0fail/0error/0skip, Maven36.522s, completed2026-10-20T01:15:02+07:00. One event/outbox and winner-consistent inventory assertions pass; automatic completion additionally dispatches the real outbox and verifies one reputation outcome. Existing Buyer cancel after successful confirmation returns403 ORDER_ACTION_NOT_ALLOWED; retained rather than changing the established contract to a generic409. No Order production source changed. Initial online resolver was stopped after unrelated remote metadata timeouts; the completed run used the existing Maven cache offline, not a repeated full-backend run.

This later test-only delta is not in the five-delta candidate build manifest below. It does not alter packaged production classes; no claim that the image includes this new test source is made.

## Candidate provenance

Builds/tests ran in isolated checkout `C:/Users/tanbu/.codex/worktrees/handoff-integration/CoVie`. A tracked-source archive plus exactly five source deltas formed2398context files: Need logger, its unit test, one real-MVC integration-test addition, API Dockerfile and Web Dockerfile. Secret/env/dump/log paths were excluded. Normalized contents of all five files match primary, isolated checkout and owned context; no mismatch found.

Manifest `.local/project-completion-source-manifest.json` SHA256:
`2F56F55277D4F3B152C04FA12891890A3696D89F741BB525BE051F2DAC6DE85F`.

| Candidate | Exact image ID | Runtime user |
|---|---|---|
| `covie-api:project-completion-candidate` | `sha256:1ebf94cdbb1ffdea6125855d26138942749c03362dbe178a9d5a4c7fa27796f9` |10001:10001 |
| `covie-web:project-completion-candidate` | `sha256:f97a6aadc46bf9811baee56fa35018673f20b8b51fa76a5f08917acc5ae31380` |10001:10001 |

Private CycloneDX SBOMs in `.local/project-candidate-security/` were generated offline with Trivy0.72.0 and network disabled. API `api.cdx.json` SHA256 `E3BAFD7AAA509C61A38E7C863AB4561F54E92D7CCAF26BD301BF618500E1CF8F`; Web `web.cdx.json` SHA256 `F9BCB550E294B14CA73127A8227A4D208E672D06A1DE60716B0512BA160ECB18`. Local tags are mutable; these IDs, manifest and dirty-source delta do not constitute an immutable registry release.

## Security disposition

All-dependency audit reports1HIGH dev-only Braces advisory; production dependency audit reports0. Upstream [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) lists no patched version at review. No forced toolchain upgrade or suppression was introduced.

Runtime scans used Trivy0.72.0, network none, offline scan, cached vulnerability DB updated2026-10-19T07:19:54Z, severity HIGH/CRITICAL with exit-code1. This is not an all-severity or continually current vulnerability guarantee.

- Initial API had2HIGH findings. Actual Alpine package policy supplied fixed libexpat/libx11 versions; a minimal explicit upgrade fixes those two. Final `api-fixed-runtime-scan.json`:0HIGH/CRITICAL, exit0.
- Initial Web had64finding rows/19uniqueCVEs, including7fixed-available perl-base rows. Minimal perl-base upgrade removes those7. Final `web-fixed-runtime-scan.json`:57rows/12uniqueCVEs,56HIGH/1CRITICAL, no fixed versions listed, exit1. **Web release security gate remains FAIL.**
- A private Node24/Trixie trial still had43HIGH findings; it was not promoted or used to justify a broad source base migration. No ignore, VEX or scanner rule relaxation was added.
- The raw zlib critical finding references MiniZip. The [Debian tracker](https://security-tracker.debian.org/tracker/CVE-2023-45853) distinguishes contrib/minizip from unaffected Bookworm binary packages. Actual candidate inventory reports zlib1g1:1.2.13.dfsg-1; no libminizip package or filename match under /usr or /app was returned (`web-minizip-inventory.log`). This supports a narrow vendor-applicability triage, not proof against every statically embedded symbol or dismissal of all findings. Raw gate remains unchanged/FAIL.

## Isolated packaged API runtime

Final API image booted the prod profile against fresh owned Postgres18/Redis8 on an internal Docker network, with generated temporary RSA/HMAC/fake provider configuration; payment, release, reconciliation, bootstrap, retention and outbox execution disabled. No recovered DB or real provider credentials used. Actual HTTP inside the container returned public health `{status:ok}`, management health UP, creation/reconciliation gauges0 and Flyway40successful/0failed.

The original PowerShell driver exited1 because its requested ephemeral host ports were not assigned on the internal network. This is preserved as a harness failure, not a product failure or a green driver run. Independent in-container HTTP verification exited0; exact result is `.local/covie-project-runtime-8054614d3d/actual-http-result.json`. This does not prove TLS, OAuth, storage-provider operations or bank settlement. Owned runtime containers/network were subsequently absent and temporary key directory had0files.

Final API image static validator passed: `.local/project-completion-api-fixed-static.log`; this validator's optional runtime phase is skipped, with independent runtime proof above kept separate. Final Web static/runtime validator passed for the exact image above: `.local/project-completion-web-fixed-runtime.log`, homepage and icon.svg/market-pin/sw.js/offline.html, non-root and forbidden-artifact checks. Temporary verifier container removed. This does not override the failing Web security scan.

## Still open

W01 individual normative assertion reconciliation; historical1280audit-layout reproduction; Web runtime security disposition; real Seller QR/payment/enforcement acceptance (currently disallowed/OFF); production target, provider/TLS/alerts/off-host recovery/field evidence. Source changes are not remote-CI verified or merged. No claim that all14work packages are complete is warranted.
