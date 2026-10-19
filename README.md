# CoVie

## Run locally in development

Prerequisites:

- JDK 25
- Node.js 24 LTS and pnpm 10
- Docker Desktop / Docker Engine with Compose

Run every command below from the repository root. The first time only, install packages and create the ignored local environment file:

```powershell
pnpm install
Copy-Item .env.example .env.local
```

Generate disposable local JWT keys if they do not yet exist. The script refuses to overwrite a key pair.

```powershell
& 'C:\Program Files\Git\bin\bash.exe' infra/scripts/generate-dev-jwt-keys.sh
```

Start PostgreSQL, Redis and MinIO:

```powershell
pnpm infra:up
```

### Fast path (after the one-time setup)

Run the full local stack with one command:

```powershell
pnpm dev
```

It starts PostgreSQL, Redis, MinIO, the API on `8081`, and the web app on `3000`. Open http://localhost:3000. To stop the local stack:

```powershell
pnpm dev:stop
```

Logs are written to ignored `.local/dev/`. The manual steps below are useful only when you need to inspect or run one process separately.

### Repeatable Public Buyer UI acceptance

With the full local stack running, create the deterministic catalog/buyer fixture and verify the populated P0 flow:

```powershell
pnpm ui:verify-local
```

This runs `/` → `/search` → Product `/items/[itemId]` → Shop `/merchants/[merchantId]` → Address List `/me/addresses` → Service and Resale `/items/[itemId]` states → `/cart` → `/cart/[cartId]` → `/checkout` → `/checkout/success/[orderId]` and then checks `/orders`. The generated local password is stored only in ignored `.local/dev/ui-fixture.json`; viewport screenshots are written to ignored `.local/dev/visual-review/`. Re-running the command abandons only the deterministic fixture buyer's active carts, so unrelated local data is preserved.

To seed without running Playwright, use `pnpm ui:seed-local`.
For the owner-approved upgraded UI where matching the historical Figma frames is no
longer required, use `pnpm ui:verify-local -CurrentDesign`. This still executes every
Buyer/Public/Seller/Admin journey and validates all201 screenshot identities, PNG
dimensions, hashes, route/state assertions, overflow and unexpected errors. Its
manifest explicitly records Figma comparison as `NOT_APPLICABLE_OWNER_WAIVER` and
all unperformed manual comparisons as `PENDING`, never as reviewed passes. The
default command remains strict and requires the hash-bound historical Figma ledger.

Regression checks for the seed and launcher are:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1
```

### Terminal 1 — API

The API reads its configuration from process environment variables; it does not load root `.env.local` by itself. Load the local file, use absolute paths for the local JWT keys, then start the API.

```powershell
Get-Content .env.local | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  Set-Item -Path "Env:$name" -Value $value
}
$env:COVIE_JWT_PRIVATE_KEY_PATH = Join-Path (Get-Location) 'infra\secrets\dev\jwt-private.pem'
$env:COVIE_JWT_PUBLIC_KEY_PATH = Join-Path (Get-Location) 'infra\secrets\dev\jwt-public.pem'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SERVER_PORT = '8081'
.\apps\api\mvnw.cmd -f .\apps\api\pom.xml spring-boot:run
```

The standard API port is `8080`. This workspace uses `8081` because an unrelated environment service occupies `8080`; use the same port in Terminal 2. Wait until the API reports that Tomcat started before continuing.

### Terminal 2 — web app

Use a separate terminal and do not import the backend `.env.local` into Next.js.
The managed `pnpm dev` launcher isolates payOS variables when it starts Web.
For manual startup, remove any inherited payOS/release variables first:

```powershell
Get-ChildItem Env: | Where-Object { $_.Name -like 'PAYOS_*' -or $_.Name -eq 'COVIE_BILLING_RELEASE_APPROVED' } | ForEach-Object {
  Remove-Item -LiteralPath ("Env:" + $_.Name)
}
$env:API_INTERNAL_BASE_URL = 'http://localhost:8081'
$env:NEXT_PUBLIC_APP_URL = 'http://localhost:3000'
pnpm web:dev
```

Open http://localhost:3000. Next.js proxies browser requests under `/api/v1/*` to `API_INTERNAL_BASE_URL` in development.

### Verify and stop

- API health: http://localhost:8081/actuator/health
- Web: http://localhost:3000/
- MinIO console: http://localhost:9001/
- Stop API and web with `Ctrl+C` in their terminals.
- Stop local dependencies with `pnpm infra:down`.

Google sign-in needs a development OAuth client ID and secret in the ignored `.env.local`. Guest routes work without them.

Plan 07 runs its PostgreSQL outbox worker in the API process. It polls every 500 ms by default and emits best-effort authenticated SSE updates at `/api/v1/me/events`; SSE is only an invalidation hint, so browser clients must refetch REST state after reconnecting or receiving `sync-required`.

Regenerate the contract on macOS/Linux/Git Bash:

```bash
./infra/scripts/export-openapi.sh
```

Regenerate it on Windows PowerShell without Git Bash:

```powershell
cd apps/api
.\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test
cd ../..
pnpm api-client:generate
```

Plan 03 media/catalog checks assume local infrastructure is already up with `pnpm infra:up`. The direct-upload flow is `POST /api/v1/media/uploads`, upload to the returned URL, then `POST /api/v1/media/{mediaId}/complete` before attaching that media to merchants or catalog items.

## Local Google OAuth

Create a Google OAuth web-client credential for local development and place its values only in `.env.local`. Register this exact local redirect URI:

```text
http://localhost:3000/api/v1/auth/google/callback/google
```

The deployed credential uses this exact production redirect URI:

```text
https://covie.vn/api/v1/auth/google/callback/google
```

The browser starts Google login through `/api/v1/auth/oauth2/authorization/google`, never Spring Security's root-level default path. CoVie requests `openid`, `profile`, and `email`; Google `sub`, rather than email, is its stable provider identity key. See [the local OAuth runbook](docs/runbooks/google-oauth-local.md) and [JWT key-management runbook](docs/runbooks/auth-key-management.md) before configuring credentials or keys.

Stop infrastructure:

```bash
pnpm infra:down
```

Production secrets never go in `.env.example`. Keep local secrets in `.env.local`, which is gitignored. Generate disposable local JWT keys with `./infra/scripts/generate-dev-jwt-keys.sh`; test runs generate their own RSA keypair in memory.

## Production operations

Plan 10 keeps production operator values out of this README. Use the source-controlled runbooks as the entrypoint:

- [Deployment and rollback](docs/operations/deploy-rollback.md)
- [Production secrets](docs/operations/secrets.md)
- [PostgreSQL backup and restore](docs/operations/backup-restore.md)
- [Metrics and alerting](docs/operations/alerting-and-metrics.md)
- [Incident runbook](docs/operations/incident-runbook.md)
- [Staging rehearsal](docs/operations/staging-rehearsal.md)
- [Pilot launch checklist](docs/operations/pilot-launch-checklist.md)

A pilot release is not approved merely because the repository builds: the staging clean-migration, security/load, backup/restore, rollback and alert rehearsal evidence in the checklist must also be green.
