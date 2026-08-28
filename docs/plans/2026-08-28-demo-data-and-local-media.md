# 2026-08-28 — Demo data v1 and local media

## Goal

Provide a repeatable CoVie demo fixture for the Grand Park pilot that is rich enough for Buyer, Seller and Admin UI/business flows, can be reset without touching ordinary data, and can be paired with a reproducible local media pack.

## Delivered

### Reference/bootstrap alignment

The branch already contains the reference bootstrap corrections required by the current V004/V005 schema and expands the pilot catalog categories used by the demo fixture.

### Database fixture

A compressed deterministic snapshot is stored at:

- `infra/demo/seed-demo-data-v1.sql.gz`

The fixture target is:

- 22 demo users
- 10 merchants
- 45 catalog items
- 60 variants
- 55 location variants
- 18 inventory balances
- 11 carts / 25 cart items
- 11 checkout sessions
- 26 orders
- 5 active need requests
- 10 need responses / 20 response items
- 18 reviews
- 24 notifications
- 7 moderation cases

Demo identities use the reserved `@demo.covie.invalid` email namespace. Demo merchant/catalog ownership is derived from the seeded merchant graph instead of relying on global destructive deletes.

### Guarded reset/reseed lifecycle

Added:

- `infra/demo/reset-demo-data-v1.sql`
- `infra/scripts/demo-data.py`

The reset discovers the demo graph first, removes dependent rows in FK-safe order, clears demo-owned media references, then deletes demo roots. It intentionally leaves shared reference/bootstrap data intact.

Mutations require both an operator opt-in (`COVIE_ALLOW_DEMO_DATA=1`) and the SQL session guard that the helper injects. Non-local database hosts are refused unless `COVIE_ALLOW_REMOTE_DEMO_DATA=1` is also set.

The lifecycle helper supports:

```text
seed | reset | reseed | status | verify
```

`verify` compares the live demo graph against the pinned expected counts above and exits non-zero on drift.

### Local media pack workflow

Added:

- `infra/scripts/import-demo-media.py`

The media pack contract is immutable for v1:

- manifest SHA-256: `0cca53918b5b8b1ec129b4a0220a54a5805d95925105062c27c0b5d726cb8f01`
- 172 total assets
- 162 AI/local images
- 10 contact QR assets

The importer validates the complete pack before upload, creates uploads through `/api/v1/media/uploads`, sends bytes to the returned storage URL, completes them through the Media API, and persists `media-map.json` for resumable runs.

It then generates `attach-media.sql` mapping media to:

- user avatars
- merchant logos
- merchant covers
- merchant contact QR methods
- catalog item images

Immediate DB attachment is optional (`--attach`) and is protected by the same demo-data mutation guard.

## Operational commands

```bash
# Database status / verification
python infra/scripts/demo-data.py status
python infra/scripts/demo-data.py verify

# Guarded lifecycle
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py seed
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reset
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reseed

# Media pack validation
python infra/scripts/import-demo-media.py --pack /path/to/demo-media-pack --dry-run

# Media upload (requires a valid CoVie access token)
COVIE_ACCESS_TOKEN='...' python infra/scripts/import-demo-media.py \
  --pack /path/to/demo-media-pack \
  --api-base http://localhost:8080

# Upload + DB attachment
COVIE_ALLOW_DEMO_DATA=1 COVIE_ACCESS_TOKEN='...' \
python infra/scripts/import-demo-media.py \
  --pack /path/to/demo-media-pack \
  --api-base http://localhost:8080 \
  --attach
```

See `docs/DEMO_DATA.md` for the operator guide and safety details.

## Validation completed in this implementation pass

- Python source for both helpers was syntax-compiled successfully before commit preparation.
- `demo-data.py --help` exposed all expected lifecycle commands.
- Prepared file checksums were compared with the intended final artifacts before the GitHub write step.
- The branch head was re-read before writing so the final commit can be created as a fast-forward from the existing `demo-data-v1` head.

## Runtime validation still required locally

This connector session does not provide the project's running PostgreSQL/API/storage stack or the operator's media-pack directory, so the following should be run after pulling the branch:

1. Start the normal CoVie PostgreSQL/API/storage services and migrations.
2. Run `COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reseed`.
3. Run `python infra/scripts/demo-data.py verify` and require all pinned counts to show `OK`.
4. Run the media importer with `--dry-run` against the final pack.
5. Login to obtain a valid access token, upload the 172 media assets, optionally attach them, then spot-check Buyer/Seller/Admin flows.
6. Run reset followed by verify/status once more to prove ordinary non-demo rows are preserved.

## Handoff

Implementation is intentionally kept on branch `demo-data-v1`. Do not merge it into `main` automatically and do not open a PR as part of this handoff. The branch is intended to be pulled and exercised locally first.
