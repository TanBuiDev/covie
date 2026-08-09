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

Stop infrastructure:

```bash
pnpm infra:down
```

Production secrets never go in `.env.example`. Keep local secrets in `.env.local`, which is gitignored.
