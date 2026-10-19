# Social V2 — current UI-correction production build/runtime evidence

Date:2026-10-16. Local configured build verification after Search price aria-labels and shared CTA minimum-height correction. Not a release, deployment or whole-goal acceptance.

## Exact source snapshot

Fresh `.local/social-v2-web-production-ui-final-20261016` context contains only three root manifests, `apps/web`, `packages/api-client`, `packages/config`, with explicit directory/file exclusions before recursion. No runtime.env, credential manifests, recovered data, node_modules, original/browser.next, test results, cache/coverage, PEM/key or tsbuildinfo copied. Unexpected reparse points fail snapshot preparation rather than following external paths.

**593files source/context SHA256 match before build and after runtime smoke.** Independently enumerated current source paths using ripgrep:593paths/593manifest entries, zero missing/extra. Context file-set independently enumerated and matched too. Not merely checking whether old manifest entries still exist. Generated hash-only evidence:
- `.local/social-v2-web-production-ui-final-source-manifest.json`
- `.local/social-v2-web-production-ui-final-source-check.json`

This supersedes the previous582file build's current-source gap after the two application UI corrections. It is a dirty-working-source local snapshot, not committed/approved release identity.

## Actual configured build

```text
docker build --progress=plain -f <fresh-context>/apps/web/Dockerfile -t covie-web:social-v2-ui-final-verification-20261016 <fresh-context>
bash infra/scripts/verify-web-image.sh covie-web:social-v2-ui-final-verification-20261016
```

Existing Dockerfile/ignore/verifier unchanged. Configured Node24/pnpm frozen dependency layer (cached where applicable), default Next16.4/Turbopack build; no webpack fallback, copied local dependencies, runtime secrets or V2 flag injection. **Build exit0**, compile**5.8s**, TypeScript**12.8s**, static**37/37**, build step**21.9s**: `.local/social-v2-web-production-ui-final-build.log`.

Authoritative local Docker image inspect: ID`sha256:abf8de7e1210b91399136b80e155ff21c689ec56f29d8c81b7747a4e06fa612b`, size405951025bytes, user10001:10001. Local metadata also contains `covie-web@sha256:abf8de7e1210b91399136b80e155ff21c689ec56f29d8c81b7747a4e06fa612b` in RepoDigests; its presence is not registry-push/signing/deployment evidence. None performed; do not call this an approved immutable release digest.

## Actual runtime and limits

Existing fail-closed image verifier ran against the newly built tag, exit0: `Web image runtime contract OK (user=10001:10001).` Evidence `.local/social-v2-web-production-ui-final-runtime.log`. It exports/lists actual artifact contents, rejects its known forbidden paths, runs owned container and checks homepage plus `/icon.svg`, `/figma/covie-market-pin.svg`, `/sw.js`, `/offline.html`200. Bash syntax and focused whitespace checks PASS. Existing unchanged stub regression for the verifier was not needlessly repeated.

Known-path listing checks are not an exhaustive secret/CVE/dependency audit. Homepage/assets200 are not authenticated API correctness, flagON browser acceptance or staging evidence. Prior isolated feature browser/HTTP proofs remain separately scoped. Current local build is still normal flagsOFF; no activation inferred.

Runtime verifier traps removed its owned containers; Docker listing for this new image has no remaining containers. Normal3000/8081 were absent and untouched;33000 was used only by the owned smoke container and is closed after cleanup. Original/browser.next not built in or mutated by Docker COPY/layers; no normal app/server or recovered DB access. Source/context593hashes still match after all smoke checks.

No application BE/FE/API/schema/client/CSS/Dockerfile/ignore/verifier edits in this checkpoint, full Maven/web test suite, old successful functional/browser/security/performance flow rerun, normal start/restart/stop, remote Git/commit/branch/CI dispatch, registry push, activation or deploy. Only tracking documents/generated local evidence changed. Whole objective ACTIVE; staging/load/private object/CDN/recovery/exact-release signoff and G5 entry conditions remain unproved.

Next action: Reconcile literal G5 entry conditions and remaining G6 gates against current source/evidence, distinguishing safe local work from infrastructure-dependent proof.
Required from you: Nothing
Completion condition: Evidence-backed remaining requirements and a safe next action, without inventing gates, waiving missing facts or calling local builds deployment acceptance.
