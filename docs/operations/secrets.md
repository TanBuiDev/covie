# Production secrets

Runtime secrets are never committed, baked into images, printed by validation scripts, or stored in release manifests.

- `DB_PASSWORD`: host/cloud secret store to API runtime environment.
- `GOOGLE_OAUTH_CLIENT_SECRET`: API runtime environment.
- `AWS_SECRET_ACCESS_KEY`: runtime environment or workload credential.
- `COVIE_RATE_LIMIT_HMAC_KEY`: runtime environment; at least 32 bytes of random key material.
- JWT private/public PEM: read-only files mounted at `/run/covie-secrets`.
- TLS private key: read-only Nginx mount.
- `COVIE_GRAFANA_ADMIN_PASSWORD`: monitoring runtime secret.
- alert webhook URL: read-only file used by Alertmanager `url_file`.

Pilot host contract: `/etc/covie/prod.env` is root-owned mode `0600`; JWT/TLS key material is readable only by the deployment/container identity that requires it; no secret is stored below the repository checkout. JWT and rate-limit key rotation is a documented maintenance operation because changing them invalidates security state/fingerprints.

Use `infra/deploy/verify-runtime-env.sh /etc/covie/prod.env` before every deployment. The validator emits configuration names only on failure.
