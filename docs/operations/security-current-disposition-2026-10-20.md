# Current dependency/image security disposition — 2026-10-20

**Status: image gate FAIL; development Braces advisory OPEN.** This is a fresh
evidence update, not risk acceptance, scanner suppression or deployment approval.
No app source, Dockerfile, lockfile, VEX, ignore, feature flag or existing data was
changed. Earlier per-component analysis remains in
`web-image-security-triage-2026-10-20.md`, but its old image digest is not substituted
for the current image below.

## Fresh exact-artifact evidence

Inspection/scan checkout:
`C:/Users/tanbu/.codex/worktrees/handoff-integration/CoVie`.
Private receipt paths below are relative to that checkout; do not publish raw
runtime/private files merely because a report links their locations.

| Check | Actual result / evidence |
| --- | --- |
| Exact image | `covie-web:local-handoff-20261020`, `sha256:40f0673b10dc4a8322503c361ee96fb3ae1ffec186bc00f9af7ec49ee068faf2` |
| Trivy database refresh | Success, database UpdatedAt `2026-10-20T13:14:22.737606427Z`, DownloadedAt `2026-10-20T14:20:41.576690077Z`; next update `2026-10-21T13:14:22.737606156Z`. `.local/trivy-cache/db/metadata.json`, `.local/project-candidate-security/trivy-db-refresh-20261020.log` |
| One refreshed exact-image scan | Trivy0.72.0, network NONE, HIGH/CRITICAL raw vulnerability scan, `--exit-code 1`, no ignore-unfixed/status filter/VEX/suppression. **Exit1: 57 rows, 12 CVEs, 56HIGH/1CRITICAL**, zero finding-key delta vs prior exact-image scan. `.local/project-candidate-security/web-current-refreshed-20261020.{json,log}` |
| Scan source binding | JSON Metadata.ImageID matches exact digest; CreatedAt `2026-10-20T14:21:14.81748978Z`; SHA256 `1386DE353F1F7E7BCB964D0D0FCE1AEE20936F7A2346A7C1B09329C940AA0D84` |
| Fresh official package-index query | Disposable exact-image container, `apt-get update` then policy for all17 flagged binary packages, exit0. Main/bookworm-updates/bookworm-security indexes fetched; **every candidate equals installed version**. `.local/project-candidate-security/web-current-policy-final-20261020.log` |
| Full dependency audit | Current `pnpm audit --json` exit1, one HIGH Braces /0CRITICAL; `.local/project-candidate-security/npm-current-all-20261020.json` |
| Production dependency audit | Current `pnpm audit --prod --json` exit0, zero findings; `.local/project-candidate-security/npm-current-prod-20261020.json`; not an OS-image pass |
| Current Braces chain | `braces3.0.3 ← micromatch4.0.8 ← fast-glob3.3.1 ← @next/eslint-plugin-next16.4.1 ← eslint-config-next16.4.1`, dev dependency of Web. `.local/project-candidate-security/braces-current-chain-20261020.log` |
| Current component check | Exact image nonroot10001; Node24.22.0; nsenter2.38.1 has no join-cgroup; fstab only unconfigured comment; no minizip under /usr,/lib, no libminizip1/systemd-homed/perl-modules package, Archive::Tar cannot load, no braces/package.json under /app. `.local/project-candidate-security/web-current-components-20261020.log` |
| Compact scan receipt | `.local/project-candidate-security/security-current-summary-20261020.json` |

The database-only download used network access to obtain the vendor DB; the actual
image scan ran with network disabled. The package-policy refresh ran in a throwaway
root container with normal Docker capabilities and no-new-privileges, no mounts,
host ports, secrets or database. Only its disposable package indexes were written,
not the image. Two earlier over-restricted cap-drop refresh attempts failed on
APT uid/permission operations and are retained as failures, not fresh-index proof;
the successful final run is the authoritative package receipt. All containers used
`--rm`; no normal app stack was started. Public version/advisory queries did not
upload repository files.

## Braces GHSA-vfj7-8cjw-p6xm / CVE-2026-93687

The current [official reviewed advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)
still lists affected versions `<=3.0.3` and **no patched version**, last advisory
update October2. Current npm-registry `pnpm view braces version --json` returns
`3.0.3`. The current audit additionally reports `patched_versions=null` and
`patched_versions_unpublished=true`. There is no evidence-backed published Braces
patch to select today.

Exposure is the observed development ESLint glob chain, not an installed standalone
Web package or production dependency audit result. A nested untrusted brace pattern
can exhaust parser recursion; therefore the advisory is not dismissed simply
because it is dev-only. Do not feed untrusted user-supplied glob patterns into the
development toolchain. Controlled checked-in lint patterns are narrower exposure,
not a formal whole-repository exploitability proof. Do not remove lint coverage,
invent a version override, substitute an unreviewed fork or upgrade unrelated
TypeScript/tooling merely to obtain an apparently clean audit.

## Current Bookworm candidates and all12 image CVEs

All entries below were rechecked against official Debian trackers on October20.
**None has a newer fixed binary candidate in the refreshed configured Bookworm
repositories.** Fixes in another Debian release or upstream branch are not validated
ABI-compatible Bookworm replacements. The table preserves component applicability
separately from raw scanner result. Eight util-linux rows per CVE are source-package
mapping, not eight independently demonstrated reachable flaws.

| CVE / rows | Fresh candidate(s) | Current official disposition and scope |
| --- | --- | --- |
| CVE-2023-45853 /1 CRITICAL | zlib1g `1:1.2.13.dfsg-1` | Debian source record remains mapped vulnerable but explicitly excludes Bookworm-produced binary code: contrib/minizip is not built. Minizip absent in current image. Separate minizip Bookworm fixed package `1.1-8+deb12u1` is irrelevant to an absent package. Preserve raw row; no need to replace ordinary zlib solely for this component. [Debian](https://security-tracker.debian.org/tracker/CVE-2023-45853). |
| CVE-2025-69720 /3 HIGH | libtinfo6/ncurses-base/ncurses-bin `6.4-4` | infocmp flaw; executable remains present. Bookworm no DSA/minor, no fixed candidate; upstream fix6.5-20251213, Debian unstable fixed6.6+20251231-1. Not an automatic whole-library false positive. [Debian](https://security-tracker.debian.org/tracker/CVE-2025-69720). |
| CVE-2026-16742 /2 HIGH | libsystemd0/libudev1 `252.39-1~deb12u2` | Bookworm postponed/no fixed candidate; vulnerable systemd-homed component absent from exact image. Upstream fixes newer258.10/261.2 etc. do not establish a safe252 library replacement. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-16742). |
| CVE-2026-41992 /1 HIGH | gzip `1.12-1` | Bookworm postponed; CLI parser remains present. Trixie fixed1.13-1+deb13u1/upstream1.14, no Bookworm candidate. Node HTTP compression is not evidence of GNU CLI use. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-41992). |
| CVE-2026-53613 /8 HIGH | bsdutils `1:2.38.1-5+deb12u3`; other util-linux binaries `2.38.1-5+deb12u3` | Restricted mount target race; no authorized fstab entry currently. Bookworm still vulnerable/no fix candidate. Trixie2.41.5-0+deb13u1 fixes its branch; upstream2.42.4 includes later legacy pinning caveat. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-53613). |
| CVE-2026-53615 /8 HIGH | Same util-linux candidates | libblkid disk-partition parser remains installed; no Web block-probing workflow demonstrated. Bookworm no fixed candidate; upstream2.41.4/trixie2.41.5-0+deb13u1 fixed. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-53615). |
| CVE-2026-54369 /1 HIGH | libacl1 `2.3.1-3` | Bookworm postponed; upstream/unstable2.4.0 introduces ACL-at ABI and vendor notes backport/compatibility concerns. Library used by base file utilities; do not delete it. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-54369). |
| CVE-2026-76642 /8 HIGH | Same util-linux candidates | Privileged failed-helper post-hooks; required mount workflow/fstab absent, old-branch code mapping not fully resolved. Bookworm no fixed candidate; unstable2.42.3-1 fixed. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-76642). |
| CVE-2026-78408 /8 HIGH | Same util-linux candidates | Scanner/Debian broad source mapping persists; current2.38 nsenter lacks affected join-cgroup feature introduced2.40. Upstream explicitly excludes Debian12; newer fixes2.41.6/2.42.3. No suppression applied. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-78408), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-55fx-f4gg-cfhj). |
| CVE-2026-78409 /8 HIGH | Same util-linux candidates | Current2.38 predates upstream affected detached-tree path; generic subdir string alone is insufficient for an entire-package exclusion. Bookworm remains mapped vulnerable/no fixed candidate; upstream/debian data disagreement retained. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-78409), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-8f2p-47x3-43mv). |
| CVE-2026-78410 /8 HIGH | Same util-linux candidates | Restricted bind/post-hook preconditions absent; old-branch mapping not completely resolved. Bookworm no fixed candidate, unstable2.42.3-1 fixed. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-78410). |
| CVE-2026-9538 /1 HIGH | perl-base `5.36.0-7+deb12u4` | Bookworm postponed/no fixed candidate; affected Archive::Tar module actually absent. Upstream module3.10/unstable perl5.42.3-1 fixed, but neither needs to be installed to fix an absent module. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-9538). |

The 17 candidate packages include every raw flagged binary: bsdutils, libblkid1,
libmount1, libsmartcols1, libuuid1, mount, util-linux, util-linux-extra, gzip,
libacl1, libsystemd0, libudev1, libtinfo6, ncurses-base, ncurses-bin, perl-base,
zlib1g. This report does not use the old empty APT cache as proof of patch absence;
the indexes were actually refreshed successfully for the exact current image.

## Minimal fix proposal / decision required

**No immediately available, demonstrated compatible patch closes these gates.**
Refreshing DB/indexes did not uncover a published Braces fix or newer Bookworm
candidate. Do not rerun unchanged application suites as if they could remove OS
CVEs; do not call refreshed scanner exit1 a pass.

The earlier one-package `mount` removal proposal is potential attack-surface
reduction, not a solution to all source-package library findings, and remains
unimplemented here. It would need its own exact new image, package/SBOM delta and
packaged smoke. Forced Essential package/library removal, scanner status filtering,
unreviewed forks, cross-release `.deb` injection or a blind base-distro migration
are not minimal compatible patches. A separately scoped maintained minimal runtime
evaluation can be proposed, but compatibility and scan outcome must be measured
before making a completion claim.

Until a legitimate compatible fix is verified, **release/image gate remains FAIL**.
Owner risk approval is required for any exception/acceptance; none is inferred
from the instruction to investigate or prepare local handoff. Any approved
exception would have to identify exact digest, CVEs, scope, controls, owner and
expiry and remain a visible exception, not “zero findings.” No exception was
requested or granted inside this subtask. Payment and production deployment remain
outside the actions performed here.
