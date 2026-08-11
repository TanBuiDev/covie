# Authentication key management

## Key handling rules

- private key loaded from secret file/secret manager.
- public key may be distributed to API instances.
- `kid` is included in JWT header.
- never commit production key.
- local keys are disposable.
- key rotation procedure is finalized in Plan 10 hardening.

CoVie signs access JWTs with RSA. Production deployments provide `COVIE_JWT_PRIVATE_KEY_PATH` and `COVIE_JWT_PUBLIC_KEY_PATH` through their secret-management and deployment configuration, not through source control. The private material is readable only by the process that signs tokens; API instances that only validate tokens may receive the public material.

## Local development

Generate a throwaway keypair with:

```bash
./infra/scripts/generate-dev-jwt-keys.sh
```

The default local paths are `infra/secrets/dev/jwt-private.pem` and `infra/secrets/dev/jwt-public.pem`. That directory and PEM files are gitignored. Replace or delete local keys freely; they are disposable and invalidate any local tokens signed by the old key.

## Rotation boundary

Every signed JWT carries the configured `kid` in its protected header. A rotation must make the new signing key and matching verification material available before issuing tokens with the new `kid`, preserve validation material for still-valid tokens, then retire the old material after its token/session window. The deployment-wide procedure, retention timing, monitoring, and rollback controls are intentionally finalized in Plan 10 hardening.
