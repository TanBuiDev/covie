# Dependabot alert 17 triage — 2026-10-13

Alert: https://github.com/TanBuiDev/covie/security/dependabot/17
Advisory: https://github.com/advisories/GHSA-vfj7-8cjw-p6xm
Package: braces 3.0.3; High stack-exhaustion denial of service for deeply nested patterns. Remote alert remains OPEN. GitHub API and advisory both report affected <=3.0.3 and no first patched version.

Authoritative source: committed pnpm-lock.yaml at f30a313 (same dependency tree as main 2f9888c2), parsed using the already installed js-yaml. Traversed importer dependencies/optional dependencies through snapshots: no production-dependency path reaches braces. Traversed devDependencies: apps/web -> eslint-config-next -> @next/eslint-plugin-next -> fast-glob -> micromatch -> braces@3.0.3. `pnpm --recursive why braces --prod` returned no path as supporting working-tree evidence; committed lock traversal is the authoritative result because local lock/package files contain unrelated barcode changes.

No direct braces/micromatch imports found in application/package source (excluding node_modules). Dockerfile builds with dev tooling but copies Next standalone/static output into the final runner, not the entire build node_modules. This supports build/lint scope; it does not prove the contents of an immutable runtime image. GitHub's runtime classification is not sufficient to establish an application request path, and dev-only reachability does not make the upstream vulnerability false.

Disposition: retain alert, no dismissal/suppression/override/fork or dependency upgrade without a reviewed fix. Do not change the dirty barcode lockfile to address an unpatched package. Before release, inspect/scan the exact runtime images as the existing release pipeline requires; build jobs must process only reviewed repository patterns. CI/source scanner success does not close this alert. Any runtime vulnerability exception requires explicit, dated approval under the pilot checklist; none granted here.

## Current standalone artifact inspection

Built Next16.4.0 from clean detached main8df8c516 in the attached public-security-hotfix worktree. Enumerated package.json files under apps/web/.next/standalone with hidden/ignored files and symlink following enabled, parsed 203 package manifests. Positive control next is present; braces, micromatch and fast-glob package names are absent. This strengthens the build/lint-only conclusion for this actual local standalone output. It is not an immutable Docker image scan or a dismissal of the upstream vulnerability. The alert remains open.
