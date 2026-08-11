# Authentication architecture v1

## Locked decisions

- Google-only first registration.
- Password required after Google onboarding.
- Google + EMAIL identities map to same user.
- Strict refresh rotation/reuse detection.
- PostgreSQL active-session validation.
- Browser HttpOnly cookies + CSRF.
- Bearer-header path reserved for non-browser clients.
- OAuth HttpSession is transient handshake state only.

## Registration and identity model

CoVie uses Google-only first registration. The first verified Google login creates a transient onboarding action, and a password is required after Google onboarding before a fully authenticated user exists. Google and EMAIL identities map to the same user when the EMAIL identity is seeded during that successful first onboarding.

Google `sub` is the provider identity key. CoVie does not auto-link an existing local account solely because its email matches a Google email.

## Sessions and tokens

Access JWTs are short-lived RSA-signed credentials. Auth sessions remain the PostgreSQL source of truth: each authenticated request validates the active session and user account status. Refresh uses strict rotation/reuse detection: every refresh token rotates, and reuse revokes the whole auth-session family. The original session expiry is absolute and does not slide during rotation.

## Browser and non-browser transports

Browser clients use HttpOnly access and refresh cookies with CSRF protection for unsafe requests. CSRF state is invalidated at authentication and logout boundaries, and the client fetches a new token before subsequent unsafe browser calls.

The Bearer-header path is reserved for non-browser clients. Bearer requests still validate the PostgreSQL session, but they are CSRF-exempt because they do not rely on ambient browser cookies.

OAuth HttpSession is transient handshake state only. It stores the requested Google-flow intent and is invalidated after the OAuth success or failure handler runs; it is not an authenticated application session.

## Plan 03 handoff

Plan 03 may rely on `users`, `user_identities`, and `system_role_assignments`; `CurrentActorProvider` resolving the authenticated user, session, and global roles; completed Google and email/password login flows; active-session revocation enforcement; `/api/v1/me`; saved PostGIS addresses; user contact methods; `AuditLogWriter`; and generated OpenAPI auth/user contracts. Flyway ends Plan 02 at `V003`.

Plan 03 must add merchant and catalog domain migrations, including location areas, merchant profiles and memberships, locations, media assets, and catalog tables. Once `location_areas` and `media_assets` exist, it adds the deferred foreign keys from `user_addresses.location_area_id` and `users.avatar_media_id`. Merchant authorization must use `CurrentActor.userId()` plus database membership/location scope; merchant roles do not belong in JWT claims.
