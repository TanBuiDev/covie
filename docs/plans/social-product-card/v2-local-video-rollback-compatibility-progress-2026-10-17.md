# Social V2 — local video rollback compatibility

Date: 2026-10-17. Whole G0–G6 goal remains ACTIVE. Previous goal turn only supplied an unexecuted prompt: no implementation progress. This turn collected the already-running rollback experiment and corrected an evidenced operational risk. No staging, production, remote Git or release operation ran.

## Actual binary experiment

Command: `.local/social-v2-video-chain.ps1 -RollbackAudit`, terminal session44985, exit0. Log: `.local/social-v2-local-rollback-audit.log`. Fixture: `.local/social-v2-video-chain-902982e19c98/`; `result.json` has `completed:true`.

Fresh owned PostgreSQL18/MinIO/network/generated RSA/login; actual packaged probe/full decode of generated H264/AAC MP4 and actual storage objects, then seeded READY mixed associations. This is a read/compatibility fixture, not another Seller upload browser journey. The same database remains across four owned API containers:

| Phase | Actual API image | Seller detail | Guest preview | Guest video Range |
| --- | --- | --- | --- | --- |
| Current ON | `sha256:c3159a643cc38c10468d53ce5e2cdf150211f0312b21753a73d47edd1d6ad5cb` | 200,3 media | 200,3 media | 206 |
| Current OFF | Same | 200,2 images | 200,2 images | 404 |
| Legacy binary | `sha256:43b150d64a54649e98034583110730879132a5efa1d71473287d7b064d3b5b9d` | **500 INTERNAL_ERROR** | 200,2 images | 401 |
| Current ON resumed | Current image again | 200,3 media | 200,3 media | 206 |

These are local image IDs, not registry/released digests or staging B-A-B identities. Phase JSON/probe logs are retained. `rollback-before-resumed.log` records the legacy exception: `No enum constant vn.covie.media.model.MediaPurpose.CATALOG_VIDEO`. The legacy image boots against later migrations but cannot hydrate the new enum. Its unknown-route401 does not prove current content authorization.

Current flag OFF is a verified non-destructive fallback for these reads, not a repair of the old immutable binary. No schema/enum/association was removed to manufacture success.

## Independent data comparison

Raw snapshots contain generated SQL strings as well as JSON because the helper executed the generator before `\gexec`. Independently extracted valid `{table,rows}` records to four `*.canonical.jsonl` files. Each has73 public tables, excluding static `spatial_ref_sys`; only auth-session `last_used_at` bookkeeping is omitted. Other fields remain deterministically sorted.

All four canonical SHA256 values equal `6D6E3873D9290D08BA324F99780BAD9E77A0575AAAF7CD802A6105DFD6F47CB4`. Evidence: `.local/social-v2-local-rollback-snapshot-verification.json`. VIDEO0/IMAGE1-primary/IMAGE2, caption, description, version0 and listing/moderation remain. This proves read-experiment preservation, not old-binary writer compatibility or object-version recovery.

## Minimal correction

Existing rollback could replace the API with any previous pinned digest and pass general health/smoke despite Seller500. Changes:

- `infra/deploy/rollback.sh`: before Docker, require source migration identity. V023+ sources require exact target `ROLLBACK_API_IMAGE` and valid `ROLLBACK_EVIDENCE_SHA256` in the source manifest. Do not infer compatibility from target migration number. Unknown compatibility fails closed without state swap/data removal. Pre-V023 source behavior remains.
- `infra/deploy/deploy.sh`: automatic error rollback passes candidate manifest as source, because candidate migrations/writes can precede `current.env` update.
- `infra/scripts/create-release-manifest.sh`: optional verified target/nonempty evidence file yield exact target/hash; omitted inputs make no claim. Invalid inputs preserve existing manifest.
- `docs/operations/deploy-rollback.md`: two-step expand/activate rollout, reader compatibility floor, conservative V023+ guard and current-binary/videoOFF fallback. Wrapper includes two focused regressions.

Attestation is trusted operator/CI metadata, **not automatic verification of evidence content**. Do not populate from approval, healthy boot, this incompatible target, local IDs or another source release. Actual release pair must rehearse populated video data/readers/writers and archive evidence. Until then the legacy target remains **NOT COMPATIBLE**. Establish compatible video-aware readers with flagsOFF before any later activation; no merge/deploy/activation occurred here.

## Verification and limits

- RED `.local/social-v2-rollback-gate-red.log`: exit1, unverified rollback accepted before correction. Docker/curl/runtime-check/smoke are isolated stubs, not deployment.
- First GREEN `.local/social-v2-rollback-gate-green.log`: exit0.
- Final `.local/social-v2-rollback-gates-final.log`: exit0. Missing source/attestation, wrong target, malformed hash and candidate-source mismatch rejected before Docker/state changes; matching attestation and pre-V023 behavior retained. Manifest fixtures verify omitted metadata, exact hash and invalid inputs preserving manifest. Four changed production scripts syntax checked. No full wrapper or BE/Web suites rerun.
- `.local/social-v2-local-rollback-cleanup-check.json`:1140 API input hashes unchanged; zero owned containers, normal/owned listeners and fixture secrets. Web artifacts unchanged. `git diff --check` exit0, existing CRLF warnings only.

No BE/FE/API/client/schema/migration/image change, normal restart, recovered data access, commit/push/deploy or activation. Script/document changes are outside API/Web image input scopes.

Next action: Reconcile every numbered G0–G6 requirement against current implementation and scoped evidence.
Required from you: Nothing
Completion condition: A complete matrix identifies and resolves remaining local work without marking external gates or legacy compatibility PASS.
