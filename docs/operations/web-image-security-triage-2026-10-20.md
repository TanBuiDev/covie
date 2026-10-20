# Web image security triage — 2026-10-20

Status: **TRIAGED, NOT A ZERO-FINDING OR RELEASE-PASS RECEIPT**.

Root follow-up: actual refreshed Bookworm repository policy confirms installed=candidate for the eight queried package groups; no compatible new patch found. New CSS-containing Web image `sha256:40f0673b10dc4a8322503c361ee96fb3ae1ffec186bc00f9af7ec49ee068faf2` retains57raw rows/12CVEs,56HIGH/1CRITICAL under unchanged scanner rules; gateFAIL. Runtime/static validator passes. No mount removal or suppression promoted. Exact evidence/provenance in `local-evidence-security-handoff-2026-10-20.md`.
This report does not authorize or perform deployment, payment, feature activation,
scanner suppression or blanket risk acceptance.

## Exact input and scope

- Image inspected: `sha256:f97a6aadc46bf9811baee56fa35018673f20b8b51fa76a5f08917acc5ae31380`.
- Input scan: isolated checkout
  `C:/Users/tanbu/.codex/worktrees/handoff-integration/CoVie/.local/project-candidate-security/web-fixed-runtime-scan.json`.
- Scan SHA256: `61C4C5D7A9D045E9EF799CD89422A25F5546607FBD43E60D2C7AED78B150A20C`.
- Scan metadata ImageID matches the inspected image. CreatedAt:
  `2026-10-19T18:05:25.795819206Z`; Debian `12.15`; cached Trivy vulnerability
  database from October19. The scan's separate ArtifactID is not the Docker ImageID.
- Actual vulnerability objects: **57 package/CVE rows, 12 unique CVEs, 56 HIGH,
  1 CRITICAL**. A PowerShell flattened property array also includes a null element;
  counting that gives58, not58 findings. Counts here explicitly exclude null entries.
- Every reported FixedVersion is empty. This means this scan has no applicable
  Debian package fix recorded, not that upstream fixes do not exist.

Only the new report is written by this task. Inspection used disposable
`docker run --rm --network none --read-only --cap-drop ALL --security-opt
no-new-privileges --entrypoint /bin/sh <exact-image> -c <read-only-command>`.
There were no mounts, host ports, secrets, application accounts or database access.
No package was removed or installed, and no CVE exploit was executed. These stronger
inspection flags are not asserted to be the eventual deployment's security settings.
Command outputs were read directly; no new separate machine-readable inventory
receipt or new scanner run is claimed by this report.

## Observed runtime inventory

Image config is UID/GID `10001:10001`, working directory `/app`, entrypoint
`docker-entrypoint.sh`, command `node apps/web/server.js`. Node reports `24.22.0`.
The entrypoint is a shell wrapper ending in `exec "$@"`; it does not invoke the
flagged compression, partition, mount, terminal or Perl tools for the normal command.

| Installed package/group | Version | Relevant observation |
| --- | --- | --- |
| bsdutils | `1:2.38.1-5+deb12u3` | Debian Essential package; depends on libsystemd0 |
| libblkid1/libmount1/libsmartcols1/libuuid1/mount/util-linux/util-linux-extra | `2.38.1-5+deb12u3` | util-linux is Essential and depends on util-linux-extra and several listed libraries |
| gzip | `1.12-1` | Essential package; executable present |
| libacl1 | `2.3.1-3` | `/bin/cp`, `/bin/tar`, `/bin/sed` dynamically link it; not safe to delete the library blindly |
| libsystemd0/libudev1 | `252.39-1~deb12u2` | Libraries installed; packages systemd/systemd-homed absent |
| libtinfo6/ncurses-base/ncurses-bin | `6.4-4` | infocmp present; ncurses-base/bin Essential; bash depends on libtinfo6 |
| perl-base | `5.36.0-7+deb12u4` | Essential; Archive::Tar module absent, see below |
| zlib1g | `1:1.2.13.dfsg-1` | dpkg depends on it; minizip binaries/packages absent |

`ldd /usr/local/bin/node` resolves glibc/libdl/libstdc++/libm/libgcc/libpthread,
not the flagged OS libraries. The installed native Sharp addon
`sharp-linux-x64-0.35.5.node` and its bundled libvips resolve glibc/libstdc++/libm/
libgcc/libpthread/libresolv/libdl, also not the flagged OS libraries in this check.
Node's own compression version is `1.3.2.1-motley-285e94b`, not OS zlib1g's version.
This is direct dynamic-link evidence, not an exhaustive dlopen, native-library,
application data-flow or Node-bundled dependency vulnerability audit.

No `child_process`, `execFile`, `spawn(`, `nsenter`, `infocmp`, `Archive::Tar`,
`setfacl`, `getfacl` or `blkid` references were found in the inspected non-test
Web features/lib/app and API-client paths. This bounded search is not a guarantee
that no third-party dependency ever invokes an external program.

## Per-CVE disposition — all 12 unique findings

In the table, **U** means the same eight util-linux source-package binaries:
bsdutils, libblkid1, libmount1, libsmartcols1, libuuid1, mount, util-linux,
util-linux-extra. Eight scanner rows do not establish eight independently
vulnerable code paths. Classifications do not change raw scanner output.

| CVE / raw rows | Affected operation and exact-image applicability | Available patch and proposed disposition |
| --- | --- | --- |
| **CVE-2023-45853**, CRITICAL, zlib1g,1 | MiniZip archive-writing overflow, not the ordinary zlib compression API. Debian explicitly says Bookworm zlib does not build the vulnerable contrib/minizip binary. Exact image has no minizip/libminizip1 package and no matching file under `/usr` or `/lib`. **NOT_AFFECTED for this reported Debian binary**, supported by vendor/package evidence; not a claim about every bundled archive implementation. | Separate Bookworm minizip fix is `1.1-8+deb12u1`, but that package is absent. Do not replace zlib or delete dpkg's library to hide this finding. Preserve raw CRITICAL and documented vendor rationale. [Debian vendor record](https://security-tracker.debian.org/tracker/CVE-2023-45853). |
| **CVE-2025-69720**, HIGH, libtinfo6/ncurses-base/ncurses-bin,3 | Overflow is in infocmp's string analysis, not all libtinfo users. infocmp exists but normal Node startup does not run it. Crafted terminal data processing by a CLI remains possible if an operator or another compromised component invokes it. **PRESENT_TOOL, NORMAL_HTTP_PATH_NOT_DEMONSTRATED**. | Upstream fix is ncurses `6.5-20251213`; Debian unstable fixed version `6.6+20251231-1`; Bookworm6.4-4 has no listed fix and vendor classifies it minor/no DSA. Keep tracked; do not force-remove Essential ncurses/bash dependencies. [Debian](https://security-tracker.debian.org/tracker/CVE-2025-69720). |
| **CVE-2026-16742**, HIGH, libsystemd0/libudev1,2 | Vulnerable feature is systemd-homed account authentication. Exact image has no systemd/systemd-homed package or homed executable; Node is the configured service. **AFFECTED_COMPONENT_ABSENT**, despite source-package library mapping. This is not a systemd-host assessment. | Upstream fixes include258.10/259.8/260.4/261.2/262, not a validated compatible Bookworm252 library replacement. No need to install/upgrade a daemon the application does not use. [Upstream advisory](https://github.com/systemd/systemd/security/advisories/GHSA-jm29-p7hh-vjhv), [Debian](https://security-tracker.debian.org/tracker/CVE-2026-16742). |
| **CVE-2026-41992**, HIGH, gzip,1 | GNU gzip CLI cross-format LZW→LZH state reuse/read overflow; executable exists. Normal HTTP compression uses Node, not GNU gzip. **PRESENT_TOOL, NORMAL_HTTP_PATH_NOT_DEMONSTRATED**, not a false-positive declaration. | Fixed upstream1.14 / Debian trixie `1.13-1+deb13u1`; Bookworm1.12-1 is postponed with no listed fix. Prefer vendor update when available, no unvalidated cross-release package injection. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-41992). |
| **CVE-2026-53613**, HIGH,U,8 | Restricted SUID mount target race. Exact image has SUID mount/umount, but `/etc/fstab` contains only the unconfigured-system comment, no user-authorized entries. **VULNERABLE_PACKAGE, REQUIRED_FSTAB_PRECONDITION_ABSENT** in this image. Deploy-time changes can invalidate this assessment. | Vendor fixes include2.41.5/2.42.2 new mount API and2.42.4 legacy pinning; no-proc legacy fallback has an additional caveat. Bookworm2.38.1 has no listed fix. Consider removing the unused mount package only, subject to runtime smoke; keep library findings visible. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-53613), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-8gj5-72r3-428g). |
| **CVE-2026-53615**, HIGH,U,8 | libblkid DOS extended-partition parser arithmetic. Library/tool presence is real; normal Web server does not probe block devices/disk images. Inspection had no block devices; that is not an inventory of a future privileged deployment. **VULNERABLE_LIBRARY, NORMAL_HTTP_PATH_NOT_DEMONSTRATED**. | Upstream2.41.4; Debian trixie2.41.5-0+deb13u1 fixed; no Bookworm fix listed. Keep tracked; never expose host block devices or add partition-processing workflows without renewed review. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-53615), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-h4rw-gv36-wmp5). |
| **CVE-2026-54369**, HIGH,libacl1,1 | Privileged pathname-based ACL operations can follow attacker-controlled symlinks. getfacl/setfacl absent, but libacl is used by cp/tar/sed, so absence of those two tools is **not** enough for NOT_AFFECTED. **VULNERABLE_LIBRARY, NORMAL_HTTP_PATH_NOT_DEMONSTRATED**; avoid privileged file maintenance on untrusted paths. | Upstream/Debian unstable2.4.0; no Bookworm fix listed. Vendor notes new ACL-at ABI and compatibility concerns; not a safe individual old-ABI backport assumption. Preserve library/essential callers. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-54369), [vendor explanation](https://access.redhat.com/security/cve/CVE-2026-54369). |
| **CVE-2026-76642**, HIGH,U,8 | Failed external mount helper followed by privileged post-hooks. No fstab entries/helper workflow configured. Candidate binary-string search found no X-mount.owner/idmap string, but that alone is not proof of source-level absence. **PRECONDITION_ABSENT; OLD_BRANCH_MAPPING_NOT_FULLY_RESOLVED**, not blanket false positive. | Upstream2.41.6/2.42.3; Bookworm no listed fix. Mount-only removal proposal reduces tool exposure, not all eight raw library rows. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-76642), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-m25x-3hj9-m26f). |
| **CVE-2026-78408**, HIGH,U,8 | Upstream says nsenter --join-cgroup was introduced in2.40 and explicitly excludes Debian12's2.38. Exact candidate nsenter is2.38.1; help has --cgroup namespace entry but **no --join-cgroup**. **FEATURE_ABSENT/VENDOR_VERSION_NOT_AFFECTED** for this feature. | Upstream2.41.6/2.42.3 fix newer branches. No need to upgrade2.38 just to acquire the newer vulnerable feature. Raw source-package mapping remains disclosed; vendor-data mismatch should be reported upstream separately. [Upstream advisory](https://github.com/util-linux/util-linux/security/advisories/GHSA-55fx-f4gg-cfhj). |
| **CVE-2026-78409**, HIGH,U,8 | Upstream affected range identifies detached-tree X-mount.subdir path in newer releases and Linux>=6.15; candidate is2.38.1. Binary does contain generic X-mount.subdir text, which does **not** prove the newer fast path exists. **UPSTREAM_VERSION_EXCLUDES_CANDIDATE; DISTRIBUTION_MAPPING_CONFLICT**; no configured fstab action. | Upstream2.41.6/2.42.3; Debian tracker still maps Bookworm source vulnerable. Keep discrepancy and raw rows visible, rather than silently suppressing them. Additional old-source/backport review is required for a formal whole-package exclusion. [Upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-8f2p-47x3-43mv), [Debian](https://security-tracker.debian.org/tracker/CVE-2026-78409). |
| **CVE-2026-78410**, HIGH,U,8 | Restricted bind source race plus privileged owner/group/mode post-hook. Required user-authorized fstab entry is absent; no Web mount workflow found. **PRECONDITION_ABSENT; OLD_BRANCH_MAPPING_NOT_FULLY_RESOLVED**, not a demonstrated HTTP exploit or unconditional NOT_AFFECTED. | Upstream2.41.6/2.42.3; no Bookworm fix listed. Same bounded mount-tool removal/hardening proposal; do not force-remove util-linux or pretend package rows vanish. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-78410), [upstream](https://github.com/util-linux/util-linux/security/advisories/GHSA-rh77-686x-2f2m). |
| **CVE-2026-9538**, HIGH,perl-base,1 | Vulnerable parser is Archive::Tar. Exact `perl -MArchive::Tar` fails module loading; dpkg's perl-base file list has no Archive/Tar; perl-modules-5.36/libarchive-tar-perl absent. **AFFECTED_MODULE_ABSENT**, not merely unused. | Archive::Tar3.10 fixes the parser; Debian unstable perl5.42.3-1 fixed; Bookworm5.36.0-7+deb12u4 still mapped vulnerable at source level. Do not install a module or newer interpreter the runtime does not need. [Debian](https://security-tracker.debian.org/tracker/CVE-2026-9538). |

## Minimal action proposals and actual removal investigation

1. **Retain the full raw scan and this component-specific triage.** There is no
   justified broad ignore/VEX/scanner-status filter here. Absent components and
   vendor-version conflicts are explanations, not a newly green configured image gate.
   No particular package FixedVersion was invented.
2. **Do not repeat blind perl-base upgrades for CVE-2026-9538.** The candidate is
   already deb12u4 and the affected module is absent. A newer ordinary Bookworm
   candidate is not established by a package cache emptied during image construction.
   The observed `apt-cache policy` has only installed versions because lists are
   removed; it is not proof about current remote repository availability. The
   official vendor pages above, not that empty cache, supply release/fix status.
3. **Only `mount` is a concretely small removal candidate identified here.** A
   network-disabled `apt-get -s remove mount` proposes exactly one removal, with
   zero upgrades/new installs. It is not Essential, normal Node/entrypoint and
   inspected app source do not invoke it, and it supplies existing SUID mount/umount.
   This supports a separately reviewed one-package runtime-image change, not proof
   that removal has been performed or all library paths disappear. Before adopting:
   build a distinct candidate, verify entrypoint/nonroot/Node/Sharp and actual
   server/static/image-processing smoke, rescan unchanged rules, compare package/SBOM
   delta and preserve the old digest. Administrative shell mount use would no longer
   work, so it must not be a relied-upon operational contract. Do not run autoremove
   or remove util-linux along with it. This will not produce raw zero findings.
4. **Reject blanket package stripping.** A simulated removal of all flagged
   packages fails dependency resolution: coreutils/sed/tar require libacl, bash
   requires libtinfo, dpkg requires zlib, apt requires libsystemd/libapt, and
   e2fsprogs requires libblkid/libuuid. A smaller mount+util-linux-extra removal also
   fails because Essential util-linux depends on util-linux-extra. Forcing Essential
   removals or deleting library files/manifests is neither minimal nor an acceptable
   way to make scanners quiet. No such action occurred.
5. **Maintain runtime containment as a separate release requirement:** nonroot,
   no privileged mode/host devices/mount authority, no user fstab entries, no
   privileged administrator operations over untrusted files, narrowly writable
   application directories. Propose cap-drop/no-new-privileges/read-only filesystem
   only with actual runtime compatibility testing; the inspection flags alone do not
   prove current Compose/deployed settings or replacement of a missing patch.
6. **For a strict zero-raw-HIGH/CRITICAL release gate**, this unchanged Bookworm
   image remains blocked. A patched vendor runtime base or deliberately minimal
   runtime may be evaluated as a separate packaging change, not an automatic
   distro migration. Simply switching to trixie is not a demonstrated solution:
   Debian also lists several of these issues there. Copying unstable `.deb` files,
   compiling replacement libc-adjacent libraries or changing to Alpine is not a
   verified compatible patch. Every candidate must keep Node24, glibc-compatible
   Sharp/native dependencies, CA trust/DNS/timezone needs and runtime behavior,
   then receive a new digest/SBOM/scan and targeted packaged smoke.

## Decision boundary and remaining risk

The **triage task is complete for all12 unique CVEs**. The **image security release
gate is not declared PASS**: raw57 rows persist, several affected packages/tools
remain installed, source-package mapping discrepancies need vendor-data resolution,
and no deployed attack-surface/containment proof or owner risk acceptance was
performed. An absence/unused-path assessment is invalidated by installing missing
modules/daemons, changing fstab/privileges/devices, adding subprocess workflows or
rebasing/upgrading the image. Re-evaluate those deltas, rather than repeatedly
re-running unchanged application suites or describing a source-only audit as an
OS-image scan.

All listed official vendor/upstream pages were consulted on2026-10-20. Failed
direct fetches for two Debian pages were not treated as evidence: ACL details were
retrieved through the official Debian indexed result and vendor page; nsenter
details use the upstream maintainer advisory, including its explicit Debian12
exclusion. No scanner rule, Dockerfile, package inventory, runtime or deployment
configuration was changed by this review.
