# Web runtime security repair — 2026-10-20

Owner explicitly approved replacing only the final Web runtime with the measured Distroless Node24/Debian13 base, pinning its digest and performing targeted compatibility checks. This is not deployment approval or acceptance of remaining project gates.

## Minimal change and provenance

`apps/web/Dockerfile` retains the existing Bookworm dependency/build stages, frozen lockfile, standalone output, static/public copies and uid/gid10001. Only the runner base changes to official `gcr.io/distroless/nodejs24-debian13:nonroot@sha256:c1326ce1aad4b976dce3a3da8b7eb5c8328e635e312309a7a0e5319d1470be0d`. The shell/package-manager removal and Perl upgrade commands are unnecessary in this shell-less base and removed. CMD supplies only the server path because the inherited entrypoint already invokes Node. No business/API/schema/dependency or scanner-policy change.

Official support and entrypoint documentation: [Distroless supported images](https://github.com/GoogleContainerTools/distroless), [Node image usage](https://github.com/GoogleContainerTools/distroless/blob/main/nodejs/README.md). Base image signature verification was not performed; do not infer it from digest pinning.

The pre-change reviewed image had raw scanner exit1:56HIGH/1CRITICAL. The rebuilt approved Dockerfile candidate `covie-web:distroless-approved-candidate`, image `sha256:6eb6deb0dab996fc61af56ac7f676fbbecc1a1003dc9ab822d2f906c7ea35dad`, has raw HIGH/CRITICAL scan **exit0, zero findings**, with both Debian13.7 OS and Node package classes scanned. Trivy0.72.0 uses the same refreshed database, network-none execution and unchanged gate; no ignore-unfixed/VEX/suppression/package-database removal. Dockerfile SHA256 `0DBF2AEC8B0EB302749B58E68DE493D588A8F891D29BEB3C91C3F4101268B02B` binds this candidate delta to the previously reviewed app payload. Exact final-commit artifact binding is recorded separately after commit.

## Targeted compatibility evidence

Private evidence is under primary checkout `.local/`; raw reports, fixture scripts and credentials must not be published merely because paths are listed.

| Check | Evidence / result |
| --- | --- |
| Docker build from actual changed Dockerfile | `web-distroless-approved-build.log`, exit0; no app source changes |
| Existing packaged verifier | `web-distroless-approved-runtime.log`, exit0: nonroot, no forbidden artifacts, default-entrypoint startup/root and four public assets |
| Raw security scan | `web-distroless-approved.trivy.json/.log`, exit0,0HIGH/CRITICAL; previous exact image exit1 remains retained |
| CycloneDX | `web-distroless-approved.cdx.json`, SHA256 `D3A75105A58AAEED316E5D82457D18DF646FE42A1C4C26BE6185D8C3781157BA` |
| SSR refresh in actual packaged Next server | `web-distroless-approved-compatibility-final2.log`, exit0: no-cookie client401, navigation303 to intended login return path, cross-site403, binding cookie43characters/HttpOnly, parallel refresh200/two cookies/private-no-store |
| Redis/DNS/HTTP/crypto interoperability | Dedicated disposable Redis8 with appendonly/appendfsync-always/noeviction and disposable fixture API; two simultaneous requests produce exactly one CSRF read and one refresh POST. No normal database, Redis, user session or payment accessed |
| Actual HTTP image optimization | Disposable generated128x96PNG present before server starts; `/_next/image` width64/quality75 returns200/image content/nonempty bytes, exercising packaged Sharp/libvips and runtime cache writes |
| Primitive evaluation on same runtime base | Node24.22.0/uid10001, Sharp PNG resize, crypto random bytes, zlib round-trip, vi-VN/Asia_Ho_Chi_Minh ICU formatting and CA bundle; real HTTPS HEAD200/TLS authorized. Separate evaluation receipt `.local/web-distroless-evaluation-20261020.md` |

Two harness issues are retained as failures, not product fixes: Docker Desktop's internal-only fixture network made the published host port unreachable despite container-local login200; an ordinary dedicated bridge network with host binding127.0.0.1 corrected observation. Next's public-file discovery did not serve a PNG added after startup (404); generating the fixture before startup corrected the probe. Default-entrypoint smoke remains separate from the image-optimization fixture wrapper. All owned fixture containers/network were removed in finally; normal runtime remains unchanged.

## Acceptance limits

### Integration CI recovery-fixture correction — 2026-10-21

First exact-head CI run39542437497 reported1206tests/0assertion failures/1error/19skips: existing `databaseCrashDuringWebhookRollsBackAndRecoveredWorkerSettlesExactlyOnce` intentionally killed PostgreSQL, then a fresh DriverManager connection passed readiness while Hikari still returned an old connection with SQLSTATE57P01 (`unexpected postmaster exit`) at the financial snapshot. This is concrete fixture-readiness evidence, not a Web regression or reason to bypass CI. The test now waits boundedly for its first successful read-only snapshot through the actual application pool, ignoring only `DataAccessResourceFailureException`; it compares that snapshot to the original outside the wait. No pool reset, financial mutation retry, weakened settlement/outbox assertions or production code change. Focused exact existing method:2passed/0failures/errors/skips, Maven51.249s, `.local/billing-crash-pooled-recovery-20261021.log` in the isolated checkout. Unaffected Web artifact/tests are retained; no local full-BE rerun. A new exact-head CI gate is still required.

The previous57 OS findings are absent from this measured minimal runtime rather than hidden by scanner exceptions. This closes that candidate's HIGH/CRITICAL image gate only; immutable reviewed-commit binding/CI remain integration conditions. No claim of zero vulnerabilities at all severities, complete application journey coverage, signed-image provenance or deployed production safety.

Braces development-only HIGH remains tracked without a compatible published patch. Actual user payment/PAID/entitlement/replay, two restricted fixture-key deletions, unproved historical runtime termination cause and production infrastructure/TLS/restore/alert/load acceptance remain separate. No deployment, invoice/link/grant creation or transfer occurred.
