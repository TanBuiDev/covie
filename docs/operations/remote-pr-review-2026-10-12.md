# Remote PR review — 2026-10-12

The active Protect main ruleset requires pull requests and the four checks backend, frontend-and-contract, release-security-source and secret-history. Deletion and force pushes are blocked; there are no bypass actors. These rules were not changed during review.

## Disposition

- Merged after all four required checks passed: #8, #9, #10, #11, #12, #13, #14, #15, #17, #18 and #21. Changes cover GitHub Actions, Maven, Spring Boot, Bouncy Castle, AWS SDK, Next.js and Zod.
- Closed #4 because its head is an ancestor of main.
- Closed #19 because Node 26 type definitions do not match the configured Node 24 runtime.
- Closed #22 because jsdom 30 caused seven reproducible Need test failures and a Vitest Blob bridge exception in CI. No product behavior was changed to accommodate this optional upgrade.
- #20: repaired a stale Vitest 3 peer reference in the lockfile. Frozen installation passed. One local full run failed an ad impression assertion; the second full run passed all 443 tests without changing product or test source. Required CI for pushed head 9dcbb46 is still running; this is not a merged change.
- #16: regenerated OpenAPI after springdoc 3.1.1. Eight differences describe existing positive/minimum constraints and LocalTime format. Generated TypeScript changes only two format comments. OpenAPI export, client check and web typecheck passed locally. Required CI for pushed head ff3489d is still running; this is not a merged change.
- #6: confirmed Draft. Its integrated UI branch diverges from current main and includes a V014 migration whose version is already used by the current schema. Keep the branch and review meaningful changes against the current implementation; do not close it as a duplicate or merge the old UI wholesale.

The PR #6 inventory compares 200 changed paths with current main and the local tree. File absence or differences alone do not prove missing functionality: some current APIs use different classes and contracts. The local inventory is `.local/pr6-current-source-inventory.json`.

## Final remote disposition

#16 passed all four required checks in run 38258838576 and merged as 178990ef. #20 passed all four required checks in run 38258804473 and was subsequently merged. The pending descriptions above record the intermediate verification sequence; both dependency PRs are now merged.

The only remaining open PR is #6, confirmed Draft and retained for selective reconciliation. The initial inventory actually contained 17 PRs: 13 merged, three closed with evidence, and one retained as Draft. Earlier progress updates incorrectly counted the initial inventory as 16.

All broad local backend/SSR changes remain preserved. No recovered database writes, deployment, force push, or protection bypass occurred.
