# Local Google OAuth setup

## Purpose

This runbook configures a Google OAuth web-client credential for the local CoVie browser flow. Keep the client secret in untracked `.env.local`; it must never be committed.

## Google Cloud configuration

Create a Google OAuth 2.0 web application client and configure these authorized redirect URIs exactly:

```text
http://localhost:3000/api/v1/auth/google/callback/google
https://covie.vn/api/v1/auth/google/callback/google
```

The first URI is for the normal Next.js development rewrite. The second is for production. Do not register a root-level Spring Security callback such as `/login/oauth2/code/google`.

Request these scopes:

```text
openid
profile
email
```

CoVie uses Google `sub` as the canonical provider identity key. Email is useful verified metadata and can seed the local EMAIL identity during first onboarding, but it is not the Google account key and never causes automatic linking of an existing local account.

## Local environment

Copy `.env.example` to untracked `.env.local`, then set the local client values:

```dotenv
GOOGLE_OAUTH_CLIENT_ID=your-local-google-client-id
GOOGLE_OAUTH_CLIENT_SECRET=your-local-google-client-secret
GOOGLE_OAUTH_REDIRECT_URI=http://localhost:3000/api/v1/auth/google/callback/google
```

Start the API and web application as described in the repository README. Begin the browser flow at `/api/v1/auth/oauth2/authorization/google`, never the Spring default root-level path. The `/api/v1` prefix is retained both when authorization starts and when Google redirects back through the Next.js rewrite.

## Test and CI behavior

Automated tests use deterministic OIDC fixtures and a generated in-memory RSA test keypair. They do not call Google and do not need a real Google credential.
