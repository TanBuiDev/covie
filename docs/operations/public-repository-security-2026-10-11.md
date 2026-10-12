# Public repository security — 2026-10-11

## Published follow-up

Initial security hardening was pushed as `9a1f844`. PR [#23](https://github.com/TanBuiDev/covie/pull/23) was then merged as `a0af364` after all four required checks passed in [GitHub CI](https://github.com/TanBuiDev/covie/actions/runs/38142516950). It upgrades production Bouncy Castle to 1.85, Vitest to 4.1.11, and patched js-yaml/brace-expansion overrides; adds a Critical development audit gate; canonicalizes OpenAPI object keys; and fixes the Linux secret-hygiene pipeline status race. See the published [scoped report](../public-repository-security-2026-10-11.md).

Authenticated GitHub metadata now verifies main-branch protection, Secret Scanning, Push Protection, and Dependabot Security Updates. Dependabot open alerts fell from 19 to one High alert for `braces@3.0.3` (GHSA-vfj7-8cjw-p6xm), in the development lint toolchain. The registry has no patched version at inspection time; the alert is not dismissed. Open Secret Scanning alerts: zero.

Clean published checkout: backend 576 tests, zero failures/errors and one skip; web 443 passed, build/typecheck/client check/lint passed (25 warnings). The separate local checkout retains the broader uncommitted Buyer/SSR/backend work. After backporting the dependencies it passed Maven verify with 831 total tests, zero failures/errors and four opt-in skips in 03:44 at 20:22:02; web 475 passed and 12 opt-in tests skipped. Logs: `.local/local-security-bouncycastle-verify.log` and `.local/local-security-vitest4.log`.

## Earlier local work and evidence

- Next.js and eslint-config-next 16.3.8; sharp override 0.35.5 and source-map-js 1.2.2. Lockfile updated with pnpm install.
- Ignore environment variants and private key stores; retain example configuration files.
- Secret hygiene rejects tracked environment files/key stores and prints file names rather than credential values. Template-filter/scanner failures remain fatal.
- CI includes a pinned Gitleaks 8.30.0 container scanning all fetched branches/tags with full Git history (compressed historical content is audited separately below). Six reviewed historical false positives have exact fingerprints in .gitleaksignore; no directory-wide exclusions.
- Dependabot checks npm, Maven and GitHub Actions weekly.
- CI/release Java tests use the already verified MinIO container digest instead of the failing upstream tag; application storage configuration is unchanged.
- Exported OpenAPI and regenerated the typed client from current backend source.
- Fixed current CI lint errors and catalog test fixtures introduced by the existing UI changes: accessible field queries, actual dropdown selection and browser image-preview doubles. Sidebar preference subscribes to external storage; Need expiry labels update every minute; merchant media state resets on resource version change, including cleared URLs.
- Checkout rollback test captures the attempted order ID and checks its child records/outbox directly, avoiding unrelated scheduler events affecting global counts. Production checkout logic is unchanged.

## Evidence

- Fetched origin refs and all tags; initial Gitleaks scanned 815 commits across local branches, remote-tracking refs and available tags. Six original hits reviewed individually: public Figma file identifier, internal Prometheus URL and sample UUID idempotency keys. Reviewed history scan: zero remaining findings. Final archive-aware scan: 816 scanned commits, zero findings, but an unexpected-EOF error on the known truncated gzip. Scanner exit zero does not prove that corrupt archive was fully scanned. CI uses the normal Git-text history gate; archive inspection is the separate limited check below.
- Current source snapshot scan: seven hits, all reviewed documentation/checkpoint text; the extra checkpoint line has a scoped inline explanation. This is not an assertion that every possible secret format is detectable.
- Found two historical SQL gzip blobs. Complete blob: 188,963 decompressed bytes; truncated blob: 122,885 recoverable bytes. Both recoverable contents use only @demo.covie.invalid email domains, contain no private-key marker and pass Gitleaks. The truncated file cannot be fully validated; names, phone/address provenance is not established by secret scanning. No Git history or branch was rewritten.
- pnpm audit --prod: zero info/low/moderate/high/critical findings.
- Web: 475 passed, 12 opt-in Redis tests skipped. Typecheck and lint passed; lint retains 27 warnings. Production build passed after changes.
- Checkout rollback/soak + OpenAPI export: 8 tests, no failures/errors/skips. Initial full backend gate: 830 tests, one global-count rollback-test failure, four opt-in skips. Full rerun: 830 tests, zero failures/errors, four opt-in skips; package BUILD SUCCESS in 03:42 at 19:17:17 Asia/Saigon.
- YAML parsed for CI, release and Dependabot. Secret-hygiene status tests and actual tracked-file check passed.

Evidence logs and redacted JSON reports are ignored under .local/public-*. Do not publish extracted SQL or raw scan logs.

## Repository configuration and remaining limits

The owner configured GitHub protections directly and they have been verified through repository metadata. Main requires pull requests and CI jobs backend, frontend-and-contract, release-security-source and secret-history; deletion and force pushes are blocked. The three security settings above are enabled. The post-merge main workflow was still running at last inspection; the successful PR workflow proves the merged source revision passed the required gate.

Remaining work: follow the unpatched braces advisory and review historical demo-data provenance, especially the truncated blob. Any history rewrite/deletion requires a separate coordinated decision; this work does not remove historical public copies. Secret scanning is not proof of a clean runtime-image scan or absence of personal data in database archives.

There is no VPS/domain or production service to deploy or audit. Repository hardening does not constitute production runtime acceptance.
