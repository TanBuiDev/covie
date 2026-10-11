# Public repository security follow-up — 2026-10-11

This is a scoped security follow-up, not a declaration that the application is free of vulnerabilities.

## Dependency remediation

- Upgrade Next.js and eslint-config-next to 16.3.8 and pin the patched sharp/source-map-js overrides.
- Upgrade production Bouncy Castle (`bcprov-jdk18on`) from 1.84 to 1.85 for GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26.
- Upgrade Vitest to 4.1.11, removing its vulnerable Tinypool dependency. Pin patched js-yaml and brace-expansion versions in the workspace overrides.
- Keep the production npm High/Critical audit gate and add an audit gate for Critical vulnerabilities across development dependencies.

## Known outstanding advisory

`braces@3.0.3` has the High advisory GHSA-vfj7-8cjw-p6xm. On the audit date, the registry reported no patched version. Its dependency path is `eslint-config-next → @next/eslint-plugin-next → fast-glob → micromatch → braces`, in the development lint toolchain. The advisory is not suppressed or dismissed. Recheck upstream releases and update the chain when a fix becomes available. Production npm audit alone does not cover Maven, development tooling, deployment images, or application logic.

## Repository controls and CI

- GitHub main branch rules require pull requests and the backend, frontend-and-contract, release-security-source, and secret-history checks; deletion and force pushes are blocked.
- Secret Scanning, Push Protection, and Dependabot Security Updates were verified enabled. The open Secret Scanning alert count was zero at inspection time.
- OpenAPI export recursively sorts object keys, preserving array order, to make contract drift detection independent of JVM reflection order.
- Secret-hygiene path checks report downstream matcher failures before consequential upstream SIGPIPE failures. The scanner still rejects sensitive paths and content.
- The advertising test unmounts components before restoring timers and global mocks, preserving the assertion that a counted delivery is not counted again.

## Verification scope

The clean PR checkout passed Maven verification with 576 tests, zero failures/errors, and one configured skip after the Bouncy Castle upgrade. All 443 web tests passed after upgrading Vitest, along with web typechecking, linting (zero errors; 25 warnings), production build, API client generation/checking, and secret-hygiene status tests on Windows and Linux. Production npm audit reported zero vulnerabilities; the development audit retains the single High advisory described above. GitHub CI remains the merge gate for the final PR revision; local results are not a substitute for that gate.

History scanning covers fetched Git text history. Earlier archive inspection encountered a truncated historical SQL gzip archive; a zero text-scanner result does not establish complete personal-data provenance of historical database archives. No history rewrite or recovered database modification is part of this change.
