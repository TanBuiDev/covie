# Security release gate

Plan 10 pins Trivy **0.72.0**. A pilot release is blocked by HIGH/CRITICAL filesystem, misconfiguration, secret or runtime-image findings unless there is a dated, reviewed exception containing the advisory/CVE, exploitability analysis, mitigation, owner role, expiry date and target fix version. There is no permanent blanket `--ignore-unfixed` exception.

Run from the repository root:

```bash
./infra/scripts/security-scan.sh
COVIE_API_IMAGE='ghcr.io/owner/covie/api@sha256:...' \
COVIE_WEB_IMAGE='ghcr.io/owner/covie/web@sha256:...' \
./infra/scripts/security-scan.sh
COVIE_API_IMAGE='...' COVIE_WEB_IMAGE='...' ./infra/scripts/generate-sbom.sh
```

The gate also requires the existing application-security matrix to remain green: CSRF negatives, IDOR 404/403 behavior, refresh-token reuse and revoked-session handling, staff location scoping, admin authorization, presigned-upload ownership, OAuth return/open-redirect checks, rate-limit behavior and the prohibition on direct Order status PATCH mapping.

Production exposure verification is split between source checks and runtime smoke. Source checks prove Nginx is the only published ingress, SSE buffering is disabled, HSTS/security headers are configured, the Web runtime is standalone, production rate limiting is enabled and API/Web management ports are not host-published. Runtime smoke must additionally prove `/actuator/*` is not public, cookies have the expected Secure/HttpOnly/SameSite semantics, object storage origin is private, and containers run non-root.

Generated SBOMs and Trivy reports are release artifacts. They are not public runtime endpoints and must not contain secrets.
