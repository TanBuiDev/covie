# Social V2 — Admin video indicator and current migration/recovery

Date:2026-10-17. Whole goal ACTIVE. Previous turn made actual rollback progress. This turn began the full acceptance matrix and found two concrete current-source gaps rather than repeating passed journeys.

## Corrected evidence-backed gaps

### Historical migration stage accidentally targeted latest

`PopulatedMigrationUpgradeTest` expected V021→V022 to execute one migration but configured no target. After real V023–025 additions, its exact current-source run failed `expected1,actual4`: `.local/social-v2-final-populated-red.log`,1FAIL/0skip. This is a test-stage assumption, not failed migration SQL or lost business data.

Pin only the V022 stage to022, preserve its existing index/heap/business checks, then explicitly apply023–025 as a separate stage. Compare all old business fields, assert new video/cleanup columns null on legacy assets, validate025 and no-op repeat. `.local/social-v2-final-populated-green.log`:1PASS/0skip,9.785s test/18.929s Maven. No applied migration changed.

### G1.1 stored-video presence still absent after G5 implementation

The original G1 phase deliberately deferred `hasVideo`; current DTO/SQL/UI and assertions still reflected that deferral. Plan G1.1 explicitly requires actual caption/video indicators. Existing video playback/detail support alone does not supply the summary indicator.

- `AdminCatalogItemSummaryResponse` adds optional-contract boolean `hasVideo` with precise stored-association description. `withPrimaryMedia` preserves it.
- `AdminCatalogReadService.SELECT` computes one bounded correlated EXISTS in the existing list/detail query: linked VIDEO association, CATALOG_VIDEO purpose, nondeleted asset. No per-item HTTP/SQL call, no new index or public API change. Stored presence is independent of playback activation; it does not expose disabled media.
- Admin row uses the same existing small text pattern as caption: server-confirmed `Có video`/`Chưa có video`; old summaries without the field render neither. No extra requests/player/preload/permission/state mutation.
- Existing deferred-phase absence assertions updated to the actual new contract; no unrelated test assertion weakened.

Actual JWT/security/PostgreSQL focused API RED1FAIL: `.local/social-v2-admin-video-metadata-red.log`, missing boolean. New UI RED1FAIL/12skip: `.local/social-v2-admin-video-metadata-ui-red.log`, missing label. READY video rows are fixture metadata, not decoded bytes; previous real upload/probe evidence is separate.

## Focused verification

| Check | Actual result |
| --- | --- |
| Full affected Admin read class + OpenAPI class | `.local/social-v2-admin-video-metadata-green.log`:14PASS/0skip,32.621s Maven; Admin8/OpenAPI6 |
| Existing Admin featureOFF guard + actual captured query plans | `.local/social-v2-admin-video-metadata-plan-guards.log`:2PASS/0skip,29.067s Maven |
| Current SQL synthetic20kitems/200kmedia | `target/admin-read-plans/measurements.txt` in private API; default20 two named queries + one20-asset batch,6.336/24.547ms SQL,134.2268ms request; filtered0.108/0.881ms; detail6named+one10asset batch. Warm local/read fixture, not production SLO or dataset with many video links |
| Full affected Admin screen component file | `.local/social-v2-admin-video-metadata-ui-green.log`:13PASS,4.70s |
| Web typecheck + targeted ESLint | `...-web-typecheck.log` and `...-ui-lint.log`, exit0 |
| Actual canonical OpenAPI semantic diff | `...-contract-diff.log`:148paths/212schemas; ONLY optional boolean `AdminCatalogItemSummaryResponse.hasVideo`; every old path/schema/property unchanged |
| Generated client/check | `...-client-generate.log` and `...-client-check.log`, exit0; generated from actual export, not hand-copied DTO |

API case proves true/false/deleted-asset distinction, no raw storage key/content URL in summary, read-only media rows, and videoOFF detail omits video despite truthful stored-presence indicator. This is not another native Admin browser journey or full suite. Existing responsive layouts/public video/commerce behavior was not replayed for an Admin-only metadata change.

## V025 canonical database recovery evidence

Existing `PostgresBackupRestoreTest` extended with separate literal multiline caption/description, ordered IMAGE/VIDEO links, READY video metadata, a deleted object's cleanup-attempt/success timestamps, Like/Save and ACTIVE comment. All public table full-row JSONB snapshots except static PostGIS table are compared after actual canonical dump/checksum/restore and source immutability checks; existing user/address/runtime/refusal safeguards remain.

First run `.local/social-v2-final-mixed-recovery.log` failed because the new test fixture incorrectly supplied category timestamps not in schema. This was not a product or restore RED. Correct only the INSERT. Final `.local/social-v2-final-mixed-recovery-final.log`:1PASS/0skip,23.54s test/32.473s Maven; fresh PG18, real pg_dump/pg_restore/checksum, Flyway025 validation/no pending migrations, restored actual API profile/address auth reads. Source stays unchanged; target auth runtime legitimately creates sessions after snapshot comparison.

These are metadata rows, not a valid clip upload/backup or S3 object restoration. Off-host/provider-version recovery and actual released binary drill remain separate external evidence, not manufactured PASS. Four existing canonical backup scripts were copied unchanged into the private relative test context with line endings normalized by the existing test, never executed against recovered data.

## Current provenance and remaining work

Eight source/test originals match tested private copies: `.local/social-v2-final-metadata-recovery-source-check.json`. `.local/social-v2-final-metadata-recovery-provenance.json` compares prior image manifests: API six input deltas (two runtime files/four test files), Web four deltas (screen/test/OpenAPI/generated types), no unrelated image-input deltas. **Earlier API c315… and Web979344… images are now historical snapshots**, not current-tree packaging. No new image build yet; settle remaining genuine observability requirements before final packaging instead of rebuilding every increment. Unchanged public/video/commerce source evidence remains scoped and reusable.

Zero owned containers/normal-or-owned port listeners. `git diff --check` exit0, existing CRLF warnings only. Normal flags OFF; no original.next, normal restart, existing/recovered data, commit/push/branch/remote/CI/activation/deploy.

New complete-plan working matrix: `v2-local-acceptance-matrix-2026-10-17.md`. It covers sections0–18, G0–G6, ADR/limits, F01–F11, named tests/commands/gates and external distinctions. It is **not yet final acceptance**: remaining12.3 monitoring coverage and final changed-source packaging/integration evidence are OPEN. Do not stop at this indicator or call retained legacy rollback compatible.

Next action: Reconcile the plan12.3 required Social/video/storage/moderation observability against existing metrics and operational assets, implementing only evidenced missing coverage.
Required from you: Nothing
Completion condition: Named monitoring obligations have truthful bounded-label sources/local verification, followed by final stable-source packaging and matrix closure.
