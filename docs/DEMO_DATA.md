# CoVie demo-data-v1

`demo-data-v1` is the deterministic pilot fixture used to exercise Buyer, Seller and Admin flows without mixing sample rows into ordinary application data.

## Files

- `infra/demo/seed-demo-data-v1.sql.gz` — compressed PostgreSQL snapshot.
- `infra/demo/reset-demo-data-v1.sql` — guarded dependency-aware cleanup for the fixture.
- `infra/scripts/demo-data.py` — `seed`, `reset`, `reseed`, `status`, and `verify` lifecycle helper.
- `infra/scripts/import-demo-media.py` — validates the immutable local media pack, uploads files through the Media API, writes a resumable media map, and can attach uploaded media to demo entities.

## Expected fixture counts

| Entity | Count |
| --- | ---: |
| users | 22 |
| merchants | 10 |
| catalog items | 45 |
| variants | 60 |
| location variants | 55 |
| inventory balances | 18 |
| carts | 11 |
| cart items | 25 |
| checkout sessions | 11 |
| orders | 26 |
| active need requests | 5 |
| need responses | 10 |
| need response items | 20 |
| reviews | 18 |
| notifications | 24 |
| moderation cases | 7 |

`verify` fails when any of these counts drifts.

## Safety guard

Seed/reset operations are intentionally blocked unless the operator opts in:

```bash
export COVIE_ALLOW_DEMO_DATA=1
```

The helper accepts local PostgreSQL hosts by default (`localhost`, `127.0.0.1`, `::1`). For an intentional remote demo/staging database, an additional explicit opt-in is required:

```bash
export COVIE_ALLOW_REMOTE_DEMO_DATA=1
```

Do not enable these variables against production.

The SQL itself also requires the session setting `covie.demo.allow_seed=true`; `demo-data.py` injects it for guarded mutations.

## Database lifecycle

The connection is resolved in this order:

1. `--db-url`
2. `COVIE_DEMO_DB_URL`
3. `DB_URL`
4. local default `jdbc:postgresql://localhost:5433/covie`

`DB_USERNAME` and `DB_PASSWORD` are used when credentials are not embedded in the URL.

```bash
# Inspect only; no mutation opt-in required
python infra/scripts/demo-data.py status

# Load the snapshot
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py seed

# Verify deterministic counts
python infra/scripts/demo-data.py verify

# Remove demo rows while preserving normal rows/reference data
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reset

# Reset then seed again
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reseed
```

Use `--db-url` after the command when a different database is needed, for example:

```bash
python infra/scripts/demo-data.py verify --db-url jdbc:postgresql://localhost:5433/covie
```

The fixture identifies demo users by `user_identities.provider='EMAIL'` plus the reserved `@demo.covie.invalid` domain and demo merchants by fixture membership / `demo-*` slugs. The reset script follows dependent references before deleting the owned users, merchants, catalog, order, need, review and media rows.

## Local media pack

The importer expects a directory containing `manifest.json` and the ready assets referenced by that manifest.

Pinned manifest SHA-256:

```text
0cca53918b5b8b1ec129b4a0220a54a5805d95925105062c27c0b5d726cb8f01
```

Expected inventory:

- 172 total assets
- 162 AI/local visual assets
- 10 merchant contact QR assets

The importer validates the manifest hash, asset count, ready paths and purpose mapping before it sends anything to the API.

Supported purposes are:

- `USER_AVATAR`
- `MERCHANT_LOGO`
- `MERCHANT_COVER`
- `MERCHANT_CONTACT_QR`
- `CATALOG_IMAGE`

### Validate only

```bash
python infra/scripts/import-demo-media.py --pack /absolute/path/to/demo-media-pack --dry-run
```

### Upload

Provide a valid CoVie access token with permission to create/complete media uploads:

```bash
export COVIE_ACCESS_TOKEN='...'
python infra/scripts/import-demo-media.py \
  --pack /absolute/path/to/demo-media-pack \
  --api-base http://localhost:8080
```

Uploads use the application Media API rather than inserting storage metadata directly. Progress is written to `media-map.json` inside the pack, so rerunning without `--force` reuses already mapped assets.

After upload, the importer writes `attach-media.sql`. To apply it immediately:

```bash
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/import-demo-media.py \
  --pack /absolute/path/to/demo-media-pack \
  --api-base http://localhost:8080 \
  --attach
```

`--attach` has the same database safety guard as the seed/reset lifecycle. Generated `media-map.json` and `attach-media.sql` are operational artifacts and should remain outside version control.

## Recommended demo refresh

```bash
COVIE_ALLOW_DEMO_DATA=1 python infra/scripts/demo-data.py reseed
python infra/scripts/demo-data.py verify
python infra/scripts/import-demo-media.py --pack /absolute/path/to/demo-media-pack --dry-run
# Start/login to the API and export COVIE_ACCESS_TOKEN, then upload/attach media.
```

Run `verify` again after any fixture change before relying on the dataset for UI or end-to-end demonstrations.
