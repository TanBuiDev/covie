# Social V2 — actual logout/login account lifecycle

## Source audit

No dedicated in-page user-account switch control was found in current application TS/TSX. Profile LogoutButton calls the existing logout API, invalidates CSRF and uses router.replace('/login'). Admin shell has a separate caller of the same API; this checkpoint does not test that Admin caller.

Email LoginForm posts through the normal CSRF-aware client, refreshes its CSRF token and invokes its default window.location.assign(safeReturnTo(...)). That is a full-document login navigation, not a social-provider state update. The activity route requires the actual authenticated server actor and keys its screen by user.id; the hook also scopes account/type/enabled and retires stale requests. Source alone is not proof of router-cache/Back behavior.

Therefore test the actual supported profile logout→login flow before adding account events, polling or requirements to detect an arbitrary HttpOnly-cookie replacement without any app action.

## Focused browser fixture

Add optional `covie.test.social-activity-real-auth=true` to the existing SocialActivityBrowserIntegrationTest and a new `apps/web/e2e/social-account-login-browser.mjs`. The original manual-cookie/pending-page scenario remains unchanged and is not rerun.

The branch creates disposable verified EMAIL identities/password hashes for A/B. The password is randomly generated, written only to the ignored temporary manifest, redacted in helper failures and deleted with that manifest. Do not print/publish the manifest. This branch skips preissued fixture sessions: all actual browser access/refresh cookies must come from the login endpoint.

Actual Next/API/PostgreSQL, no auth/activity response mocking, no context.addCookies/clearCookies and no synthetic focus event. Browser uses localhost:3000 through the established isolated HTTP/HMR forwarding; normal services are not started/restarted.

## Actual observed chain

1. Open real LoginForm with returnTo private activity; enter A credentials and submit: POST login200 with X-CSRF-Token. Both access/refresh cookies exist and are HttpOnly. JavaScript document marker is gone after login, proving a new document realm.
2. Actual activity GET200 renders A's liked rows. Navigate through the real profile link, assert A profile, click real LogoutButton: POST logout204 with CSRF, route becomes /login.
3. Native browser Back targets the previous private activity route. Actual activity request returns401, with the existing refresh/sign-in bridge returning to login; no visible A activity is observed after logout.
4. Enter B credentials in the real form: second POST login200/CSRF. Its document marker is also gone. Actual activity GET200 renders B's sole liked row and no A rows.
5. A MutationObserver/binding watches the visible activity panel across DOM updates after logout, not unrelated public catalog names. Recorded A-private flashes0; uncaught page errors0. This is a local DOM-observation assertion, not a claim about every platform's first paint/BFCache behavior.
6. Java checks full JSONB of catalog_items/catalog_social_reactions/catalog_social_comments unchanged. Expected auth-domain writes are separately verified: exactly one revoked A session and one nonrevoked B session. This is not a claim of no database writes during authentication.

## Validation

Sequential isolated API command:

```text
mvnw.cmd -o -Dtest=SocialActivityBrowserIntegrationTest -Dcovie.test.social-activity-browser=true -Dcovie.test.social-activity-real-auth=true -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-account-login-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-account-login-browser test
```

- `.local/social-v2-account-login-browser.log`: **1 PASS**, test48.27s, Maven57.310s; no failures/errors/skips.
- `evidence.json` in the matching directory records login200/logout204/login200; activity A200/none401/B200; two document resets; no manual cookie changes/synthetic focus/privateA flashes/page errors. Chrome155.0.8059.84.
- Targeted helper ESLint and diff check pass. Source/private copy hashes match login form/API, profile logout button and activity hook.
- Credential manifest absent after finally; owned children closed by fixture cleanup. No production source/API/schema/client/migration change, broad auth/BE/web suite, recovered/runtime DB access, normal restart, activation, commit/branch/remote operation or deployment.

## Conclusions and remaining gates

Keep the existing auth and activity reset implementation. Same-tab email logout/login already creates a fresh document/actor state and passes the actual Back check. This narrows the earlier “account switch without trusted prop/event” gap: a manual cookie swap is not the same flow, and is not grounds for inventing polling.

This is G4 actual activity proof. G3's provider is in-memory and a new document cannot reuse its old JavaScript state, but this scenario does not render/measure G3 viewer flags. Native cross-tab focus, background account changes, OAuth, Admin logout, production build/BFCache, interrupted login, in-flight mutation responses and staging remain outside this proof. Do not mark the entire privacy/release gate complete.

Whole V2 ACTIVE; normal V2/video flags OFF; staging/release UNVERIFIED.

Next: inspect the concrete G3 overlap between a pending Like response and focus-triggered summary revalidation. The adapter serializes same-item mutations, but that is not proof against an older summary read overwriting a later server-confirmed result. Add a single targeted regression only for that explicit G3 consistency requirement before proposing changes.
