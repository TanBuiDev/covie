# CoVie

## Local bootstrap

Prerequisites:

- JDK 25
- Node.js 24 LTS
- pnpm 10
- Docker Desktop / Docker Engine + Compose

Start infrastructure:

```bash
pnpm infra:up
```

Run the API:

```bash
cd apps/api
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

Windows PowerShell equivalent:

```powershell
cd apps/api
.\mvnw.cmd "-Dspring-boot.run.profiles=local" spring-boot:run
```

Run the web app in another terminal:

```bash
pnpm web:dev
```

Verify:

- http://localhost:8080/api/v1/health
- http://localhost:8080/actuator/health
- http://localhost:3000/
- http://localhost:9001/

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
