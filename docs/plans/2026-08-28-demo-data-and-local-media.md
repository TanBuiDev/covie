# 2026-08-28 — Demo data v1 and local media

## Goal

Provide a repeatable CoVie demo fixture for the Grand Park pilot that is rich enough for Buyer, Seller and Admin UI/business flows, can be reset without touching ordinary data, and can be paired with a reproducible local media pack.

## Delivered

### Reference/bootstrap alignment

The branch already contains the reference bootstrap corrections required by the current V004/V005 schema and expands the pilot catalog categories used by the demo fixture.

### Database fixture

A compressed deterministic snapshot is stored at:

- `infra/demo/seed-demo-data-v1.sql.gz`
- deterministic generator: `infra/scripts/generate-demo-seed.py`

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

The downloadable media pack contract for v1 is:

- manifest SHA-256: `87e267134a880afc3b2de2e16bec3465d4de73cfff52cfc63663f4e0064214f7`
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
  --api-base http://localhost:8081

# Upload + DB attachment
COVIE_ALLOW_DEMO_DATA=1 COVIE_ACCESS_TOKEN='...' \
python infra/scripts/import-demo-media.py \
  --pack /path/to/demo-media-pack \
  --api-base http://localhost:8081 \
  --attach
```

See `docs/DEMO_DATA.md` for the operator guide and safety details.

## Validation completed in this implementation pass

- The downloadable ZIP was reopened and tested with Python's ZIP integrity check; no corrupt entry was found.
- The pack contains 172 manifest records and exactly 60 product ready images.
- The downloadable pack manifest hash was recalculated as `87e267134a880afc3b2de2e16bec3465d4de73cfff52cfc63663f4e0064214f7`.
- `infra/scripts/import-demo-media.py` was syntax-compiled after syncing that hash.
- `demo-data.py --help` exposes all expected lifecycle commands.

## Runtime validation completed locally — 2026-09-03

The originally committed gzip blob had the expected Git hash and gzip header but failed full decompression with `EOFError`. It was replaced by deterministic generator output and verified end-to-end:

1. `generate-demo-seed.py --check` passed; a full gzip read produced 188,963 SQL bytes ending in `commit;`.
2. Guarded `reseed` committed against PostgreSQL 18/PostGIS with the current V001–V014 schema.
3. `demo-data.py verify` reported all 16 pinned counts as `OK`.
4. `.local/demo-media/covie-demo-media-v1` passed validation with 172 assets (162 generated visuals + 10 QR codes).
5. A reserved demo admin authenticated through the local API; all 172 assets uploaded and attached as 22 avatars, 10 logos, 10 covers, 10 QR contacts and 120 catalog images.
6. Public catalog discovery returned populated items and a returned WebP media URL responded HTTP 200.

The local MinIO initializer grants anonymous `download` only for `covie-local`, matching the API's local public media URLs. Production object storage remains private. After a database reseed, use the importer with `--force --attach` because reset intentionally removes demo media metadata.

## Handoff

The demo workflow has been integrated and exercised on `codex/integrated-ui-completion`. Merge/release still follows the existing PR review and staging gates; this local validation is not staging evidence.
