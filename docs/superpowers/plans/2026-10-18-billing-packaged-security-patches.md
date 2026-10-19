# Packaged API Security Patch Plan

> **For agentic workers:** Execute in the isolated Billing worktree with targeted evidence and read-only review. No broad upgrade/commit/activation.

**Goal:** Remediate14 actual packaged Java HIGH/CRITICAL findings without changing API/business rules or unrelated dependency families.

**Architecture:** Keep Spring Boot4.1.1/AWS/Springdoc/PostgreSQL/FE unchanged. Override only supported patch BOM properties for Jackson2/3 and Tomcat; remove the stale Netty pin so existing Boot BOM supplies4.2.17.Final. Existing fake-provider, authentication/serialization and canonical OpenAPI tests protect compatibility; rescan the actual rebuilt JAR with the now-cached official vulnerability/Java databases.

**Tech Stack:** Maven/JDK25, existing tests, Trivy0.72.0 rootfs. Initial rootfs scan186packages/14findings is the failing security gate, not a manufactured speculative exploit test.

## Chunk 1: Verify and patch

- [ ] Confirm vendor patch/advisory evidence: Jackson2.21.7/3.1.7 official FasterXML release notes; Netty advisory GHSA-c4c3-7fpv-j4q5; Apache Tomcat11.0.25 security page. Exact14IDs in `.local/billing-packaged-api-vulnerability-summary.json`. Maven Central HEAD200 for all four target BOM/artifact versions recorded `.local/billing-security-patch-central-availability.json`.
- [ ] Change only `apps/api/pom.xml`: remove explicit netty4.2.16 pin (Boot parent already4.2.17); add `jackson-2-bom.version=2.21.7`, `jackson-bom.version=3.1.7`, `tomcat.version=11.0.26`. Vendor primary review revealed11.0.25 still affected by HTTP/2 request-header mix-up CVE-2026-86350 and WebSocket issue CVE-2026-87022, fixed11.0.26; choose that same-branch security patch rather than knowingly stop at11.0.25. HEAD200 availability retained separately. Do not alter parent/dependencies/flags, suppress scanner findings or add CVE allowlists.
- [ ] Online targeted package command uses real existing PSP HTTP/signature tests + existing subscriber effective-price renewal + OpenAPI export; inspect actual selected counts and contract drift. Download only required patches, no blanket dependency update.
- [ ] Compare old/new packaged dependency inventory: changes confined to Jackson/Netty/Tomcat families; retain explicit unexpected delta if any before proceeding.
- [ ] Rootfs actual fat JAR scan uses cached vulnerability+Java DB with networkNONE and strict HIGH/CRITICAL exit1 policy. Assert recognized nonzero package count, complete analysis, exact new artifact SHA and zero unresolved findings; never accept initial empty fs scan as green.

## Chunk 2: Compatibility and broader gate

- [ ] Existing new packaged-JVM restart test runs against patched current artifact, not old SHA. Regenerate/check canonical typed client only through official scripts if export changes; unexplained API semantic drift is not acceptable.
- [ ] Full Maven once all new observability implementation stabilizes; do not replay full repeatedly between each dependency/metric edit. Unchanged Web539/native UI evidence retained; no unrelated FE upgrade.
- [ ] Source scan/provenance/report+AGENTS update. Any scanner finding persists as unresolved until patched or specifically evidenced, no whole-goal completion merely from patch availability.

Primary references: https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7 ; https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7 ; https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5 ; https://tomcat.apache.org/security-11.html .

Next action: Read-only review, minimal Maven property patch and targeted actual package/scan.
Required from you: Nothing
Completion condition: Current packaged dependencies clear the actual recognized-package security gate with existing payment/API behavior preserved; remaining whole-plan metrics/gates continue.
