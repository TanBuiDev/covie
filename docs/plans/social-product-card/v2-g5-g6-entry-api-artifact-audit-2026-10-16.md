# Social V2 — literal G5/G6 gates and API artifact verifier

Date:2026-10-16. Requirement/evidence audit plus one proven G6 verifier correction. Not phase completion or release acceptance.

**Subsequent owner override:** "Bỏ qua phần staging và tiếp tục triển khai hoàn tất dự án". The blocked/entry decision below is historical and must not stop implementation. Continue G5/local completion through `v2-g5-local-implementation-plan-2026-10-16.md`. First bounded full-object download increment is implemented/tested in `v2-g5-bounded-object-download-progress-2026-10-16.md`; actual probe/purpose/mixed/UI work continues. Normal flagsOFF, no invented staging/deployment proof. Prior API image ceases to prove current source after this storage change; web input scope unchanged.

## Literal entry and remaining scope

- Plan7 says G5 begins after G1–G3 plus cost/content-safety/storage approval. Owner approved **all** separate requirements; human approval is not pending and must not be requested again. Existing authorization and normal flagsOFF remain unchanged.
- G1.5 requires real Admin item lookup without report, valid-case moderation/audit/public effects and preserved existing protection. G2.5 requires real Seller caption/create/edit/server-confirmed media/moderation and old create compatibility. G3.5 requires real caption/gallery, bounded/non-N+1 data, preserved Catalog/interactions and enforced moderation. Recent isolated actual native/API/PG/MinIO chains materially strengthen their local evidence; do not retain obsolete “missing UI” or invent every possible order/type/browser combination as entry gates.
- G1.4 explicitly names staging browser, G2 migration work names sample staging measurements;12.4 requires real staging and14 says stop on absent staging/evidence. Earlier owner continuation explicitly allowed G1–G4 local with flagsOFF, not fabricated staging. No staging, deployed storage/CDN proof, actual costs/load/recovery/exact-release evidence has been supplied. Do not silently waive those factual conditions or mark entire phases accepted.
- Source still has no CATALOG_VIDEO, catalog probe/poster/mixed-media path; current upload video allowance is AD_BANNER. These are **missing G5 implementation work**, not a circular rule that G5 code must already exist before starting G5. ffprobe/ffmpeg absent from current workstation PATH is a preparation fact, not a newly invented requirement to install host tools or proof about a deployed image.
- ADR-E explicitly treats poster/codec/transcoding/cleanup/moderation/storage-cost/browser readiness as activation gates. Approval is not executable-pipeline or operational proof. Do not treat every activation condition as a new preimplementation approval request. Current explicit G1–G4 local continuation does not manufacture G5 technical/staging evidence.
- G5.5/PR08 allow caption+image release while video remainsOFF **only if that release's gates pass**. This neither forces video activation for an image release nor removes unimplemented F06/video from the full requested goal.
- G6/12.1–4 still requires populated staging migration/backup, actual Phase4 and named role/browser regressions without skipped fixtures, current immutable candidate/CI/contract/security evidence, measured staging performance/SLO, backup+restore+app rollback and rollout/signoff. Local image digest metadata or isolated browser successes are not deployment evidence. Full exact-candidate suites remain a final gate; do not continually run them during targeted unchanged iteration.

Safe local artifact/security preparation remains possible without accessing recovered data or starting production-like services on normal ports. This audit found a concrete defect in that preparation, addressed below.

## Proven API image verifier false PASS

`infra/scripts/verify-api-image.sh` still used repeated `docker export | tar -tf - | grep -qx <path>` inside an if. The already-corrected web verifier was not rerun or refactored. New focused `infra/scripts/tests/api-image-artifact-gate.sh` uses real tar archives and stub Docker only, explicit runtime=false, benign fake `.env`/`.git`/source/PEM-path contents in one guarded disposable fixture; no actual daemon, credentials, env file, secrets, database, network or runtime startup.

RED exit1 `.local/social-v2-api-image-artifact-red.log` proves four false acceptances:
1. Directory `app/.git/` missed by exact grep without trailing slash.
2. Directory `app/src/` missed similarly.
3. Exporter exit7 treated as no forbidden match, then static success.
4. Invalid tar listing also treated as no forbidden match, then static success.

Existing `.env` file/private-key-path file detection already worked. No claim an actual runtime image leaked secrets; this is a proven fail-open required verifier, not an imagined backend business bug.

Minimal fix: one checked full export/list into temp file, fail closed on export/tar failure, exact fixed-string file-or-directory-slash checks for the same four existing forbidden paths, temp cleanup in both static/runtime traps. Existing non-root check, runtime env/secret requirements, Docker run, health polling and explicit static-only skip message unchanged. Required artifact wrapper adds the new focused regression; no unrelated behavior or application API changed.

GREEN exit0 `.local/social-v2-api-image-artifact-green.log`: four forbidden paths and export/list failures rejected, clean archive accepted with **static-only/runtime-skipped message**, not a fake runtime PASS. Bash syntax of verifier/new test/wrapper and focused whitespace checks PASS. Disposable stub fixture cleaned, no leftovers. Full production-artifact wrapper/unchanged web or recovery tests not rerun; remote CI not dispatched. The corrected verifier has not yet inspected a new real current API image in this checkpoint.

## Current delivery state

### Subsequent prerequisite decision after actual local recovery

Current-state recheck2026-10-16: `main`/HEAD`bb2e06b41282c8e9205c35a13e7b6adec68ceaa2` plus preserved dirty work. All1116 API and593 web manifest input hashes still match their already-built images; no reason to rebuild or rerun unchanged business tests. No remaining owned Docker containers. The later API packaging/static inspection, actual prod boot/empty migration and actual populated Social backup→separate restore→same-image prod reads supersede their earlier pending status in this historical report. See `v2-current-api-production-image-progress-2026-10-16.md`, `v2-api-production-runtime-health-progress-2026-10-16.md`, and `v2-social-data-backup-restore-progress-2026-10-16.md`. The last check compares all73 public tables' complete JSONB rows except PostGIS static data, not just legacy counts. It is still not a distinct-release rollback or staging acceptance.

| Remaining requirement | Actual current state / next permitted work |
| --- | --- |
| G1–G3 full prerequisite evidence | Local native/API/PG/storage implementation evidence retained. G1.4's staging browser and G2's populated staging migration measurements remain unproved. Owner's local continuation explicitly coveredG1–G4 with flagsOFF; do not silently waive factual entry conditions or request the already-granted business approvals again. |
| G5.2 backend / G5.3 frontend | Still NOT IMPLEMENTED: enum has noCATALOG_VIDEO and current Social gallery renders images. Dedicated permission/verified upload/probe/poster/ordered mixed attachments/public policy, seller uploader and controlled playback/fallback remain actual implementation work, not an already-passed phase. Start after the statedG1–G3 prerequisite; missing code is not itself a circular prerequisite. |
| G5.4–5 video verification/activation | Named malformed/size/codec/probe failure/quota/cleanup/mixed ordering/browser/network/moderation cases and measured operational/storage readiness remain unproved. Approval persists; no new cost/limit/contract vote is needed. Normal video staysOFF. Do not infer working providers or activation from a workstation tool installation. |
| G6 staging/Phase4/load/storage | User confirmed no staging; no operator-provided HTTPS target/config/manifest is available. Do not probe a guessed domain, substitute localhost performance/private-CDN evidence, create paid infrastructure or execute a staging wrapper against fabricated hooks. Real role journeys, populated migration/load/SLO and object/CDN policy evidence require that environment. |
| G6 recovery/rollback | Local actual populated Social recovery nowPASS. Off-host backup/storage-object recovery and an actual candidateB→previously-releasedA→B digest/app drill remain unproved. No authoritative previous-release manifest/digest was supplied; do not arbitrarily choose a local image or baseline commit and call it the previous release. |
| G6 exact candidate/CI/release | Dirty local implementation is not a published immutable PR head/release. No new commit/push/PR/deployment is authorized by this checkpoint; existing local images are not remote CI/signing/release evidence. Final exact-candidate suites, matching CI artifacts, operator staging evidence and actual release sign-off remain required. |

Decision: no additional evidenced application defect or unmet safe local G1–G4 implementation task was identified in this recheck. Do not manufacture tests, repeat passed scopes, silently startG5 before its stated prerequisite, or reduce the full objective to caption/images merely becausePR08 allows an image-only release. Next full-goal step is externally gated. The same dependency has now persisted through three consecutive goal turns after actual local recovery progress. Latest recheck: no new operator target/config/fixture/manifest or prior-release identity supplied, currentCOVIE_STAGING_BASE_URL/COVIE_RELEASE_STATE_DIR unset, HEAD stillbb2e06b4. This is an actual external impasse, not a verified wait on a live job. The blocked threshold is met; request goal statusBLOCKED, retain the whole objective and stop automatic repetition. This is not completion, owner-requested pause or withdrawal of existing approvals.

Unblock input, when infrastructure exists: the real staging HTTPS URL and a pointer to the operator-managed config/account-fixture/manifest location; authoritative candidate and prior-release identities for the actual rollback drill. Keep passwords/keys/tokens out of chat and evidence. This records the dependency already known to be absent; it does not ask the owner to repeat feature/ADR approvals or pretend staging now exists.

Latest actual web image/build remains the593file current UI snapshot in `v2-ui-final-production-build-progress-2026-10-16.md`; this infra-only correction does not invalidate its inputs or justify rebuilding it. API current-source packaging and the corrected real-image guard are a distinct safe local next action, with runtime health requiring disposable DB/Redis/generated keys, never existing runtime credentials/data.

Normal3000/8081 and verifier ports33000/38080 absent/untouched; no real container started/stopped in this stub-only checkpoint. No application BE/FE/API/schema/client/CSS/migration changes, Maven/web suites, recovered/runtime DB access, normal restart, remote Git/commit/branch/push, activation or deploy. Whole objective ACTIVE; all normal V2/video flagsOFF. No new approval request or staging invention.

Next action: Follow the superseding owner staging waiver and implement actual bounded media probing, then remaining catalog video/mixed-media backend/UI scope locally.
Required from you: Nothing
Completion condition: Full approved implementation and proportionate local evidence; do not stop at helper tests or label unrun staging/deployment PASS.
