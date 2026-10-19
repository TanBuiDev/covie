# Social V2 — current-source production web build và image guard

Ngày: 2026-10-16. G6/local build/security preparation, không nghiệm thu toàn V2 hoặc deploy. Base803b25b; working HEADbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty changes, no new PR. Lượt trước là progress: V2 perf scenario/full-body measurement RED/fix/7checks. Build trước đó pre-G1-access-denial fix nên không chứng minh current source; lượt này đóng đúng khoảng trống đó, không chạy lại full BE/web suite.

## Current build evidence

- Fresh whitelist context `.local/social-v2-web-production-current-20261016`:3root manifests, apps/web, api-client, config; không `.env*`, `.local`, `node_modules`, `.next`, test-results/playwright artifacts/tsbuildinfo. Không copy actual credentials, runtime/recovered data hoặc sửa original/browser `.next`.
- **582files SHA256 match** trước build và sau final smoke. Source path-set independently enumerated with the same exclusions:582current files/582manifest entries,0missing/extra; không chỉ so các file cũ còn trong manifest.
- Manifest hash-only `.local/social-v2-web-production-current-source-manifest.json`; post-build match/state/path-set logs cùng prefix. Đây là local source-snapshot evidence, không phải committed release identity.

```text
docker build --progress=plain -f <fresh-context>/apps/web/Dockerfile -t covie-web:social-v2-current-verification-20261016 <fresh-context>
bash infra/scripts/verify-web-image.sh covie-web:social-v2-current-verification-20261016
```

Configured Dockerfile unchanged: Node24, pnpm frozen install, default Next16.4/Turbopack `next build`, standalone runner + static/public. **Build exit0**, compile5.6s, TypeScript11.5s, static37/37, build step20.3s: `.local/social-v2-web-production-current-build.log`. No webpack fallback or secret/flag injection.

Local image ID `sha256:920fa02303c70bb88b3d8cd94683443d9001ea8bf0389c54ab7d752e0a573246`,405948783bytes, user10001:10001. Not pushed/signed/deployed; do not relabel this dirty-source local artifact as an approved release digest.

## Image verifier defects found and fixed with evidence

While checking the required runtime artifact gate, source had an exact grep against `app/.git`/`app/apps/web/src`, but tar lists directories with trailing slash. New isolated fixture uses real tar archives and **stub Docker/curl**, only fake sentinel files, no daemon/network/auth:

1. `infra/scripts/tests/web-image-artifact-gate.sh` **RED exit1**: old guard accepted forbidden `app/.git/`; `.local/social-v2-web-image-artifact-red.log`.
2. Added export-failure probe **RED exit1**: even exporter exit7 was treated as no forbidden path and gate continued to success. `.local/social-v2-web-image-inspection-red.log`. This is confirmed fail-open inspection handling, not a claim an actual deployed image leaked secrets.

Minimal `infra/scripts/verify-web-image.sh` correction: one checked export/tar listing into a temporary file, fail before runtime smoke if inspection fails, exact fixed-string checks accept both file path and directory slash. Full listing consumption also avoids an early-quit grep pipeline. Existing user/homepage/four-public-assets checks unchanged; temp listing and owned containers cleaned in traps. `verify-production-artifacts.sh` now calls the focused regression alongside its existing test.

Final fixture **exit0** `.local/social-v2-web-image-artifact-final-green.log`: rejects fake `.env` file, `.git` directory, web/src directory and exporter failure, accepts clean archive. This validates gate control flow, not a real image's contents. No new application code or image rebuild was needed for verifier-only changes.

## Actual final runtime smoke

After the gate correction, ran the verifier against the actual newly built image: **exit0**, `Web image runtime contract OK (user=10001:10001)`, `.local/social-v2-web-production-current-runtime-final.log`. It actually inspects the image listing, checks known forbidden paths, starts owned container and requires homepage + `/icon.svg`, `/figma/covie-market-pin.svg`, `/sw.js`, `/offline.html`200. The earlier pre-final-verifier smoke log is historical, not substituted for this final run.

Shell syntax for verifier/production-artifact wrapper/new test and targeted diff whitespace checks PASS. No broad production-artifacts suite/remote CI dispatch or exhaustive secret scanner claimed. Known-path inspection is **not** exhaustive secret/dependency/image security approval. No API/authenticated production browser/content correctness inferred from homepage/assets200.

Owned real-image containers removed after smoke; no listeners3000/8081/33000 at final observation. Stub fixtures cleaned,0leftovers. No migration/apply, BE/FE/API/OpenAPI/client behavior change, full Maven/web test rerun, recovered/runtime DB access, normal service start/restart/stop, remote Git/commit/branch, registry push, activation or deployment. Normal V2/video flags stay OFF. Source582files still match after verifier changes (outside web build inputs).

## Status and remaining requirements

Current source **local configured production build/runtime asset gate PASS**; supersedes earlier pre-G1-fix build gap. Full12.4Frontend/E2E/Security/Release is not DONE: scoped test/capture coverage, real staging/browser/load/object-CDN privacy/recovery/exact-head CI/sign-off remain separate. G5 scope retained with entry gates and video OFF; no completed-goal claim.

Next action: Trace the actual guest Home/Search setup and existing browser coverage for11.3.1, then verify the missing real guest read/toggle/no-mutation path using current fixtures if not already proven.
Required from you: Nothing
Completion condition: Guest scenario scope and current evidence are established without counting an issued-buyer Search run as guest Home/Search acceptance.
