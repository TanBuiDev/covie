# Social Product Card 2.0 — G4 pending-page reconciliation

## Reproduced defect and minimal correction

The existing hook intentionally refreshes current-state activity on focus,
online and visible-tab events. Its shared pending guard also rejected that
refresh while a first/page request was pending. Consequently a tab returning
after access changed could keep previous rows and accept the old pending result
without checking the current session/visibility policy.

One focused regression reproduces it: initial private rows, held continuation,
focus event, fresh access denied, old completion. Before the fix it observes two
calls instead of the required fresh third request:
`.local/social-v2-g4-focus-race-red.log`, one failure (four unchanged cases skipped).

The hook now retires and aborts its current request session before starting a
fresh first-page reconciliation. Completion of a retired session is ignored even
if transport does not honor abort. Existing scoped account/type resets, append
deduplication, cursor retry and explicit load-more pending guard are preserved.
No authentication infrastructure, API contract or backend behavior changed.

## Verification already completed

- `.local/social-v2-g4-focus-race-green.log`: **8 passes, 4.15 s** for the changed
  hook and its consuming screen. The regression verifies abort, a new first-page
  request, 403 clearing and a deliberately non-abortable late response ignored.
- Final web typecheck and targeted hook/test ESLint pass:
  `.local/social-v2-g4-focus-race-typecheck.log` and matching `-lint.log`.
- `.local/social-v2-g4-browser-probe.cjs` bundles the actual React screen, hook and
  generated API adapter with installed dependencies. Playwright intercepts this
  browser context's localhost:3000 HTTP requests only; existing servers are not
  changed. Responses and actor cookie are fixtures, not real JWT/API proof.
- Browser RED reaches the actual screen and keeps old rows/loading-more after
  the focus event. Final browser fixture passes in Chrome 155.0.8059.40:
  `.local/social-v2-g4-browser-probe-final.log` and `-probe.json`. Recorded requests
  are A first page, A held continuation, then B fresh first page returning 403.
  Old private rows and continuation controls are absent after reconciliation.

Attempts to generate native focus using headless tab activation/CDP did not emit
the needed focus event. They were harness limitations, not product RED evidence.
The successful browser probe explicitly dispatches a **synthetic focus event**;
its event record has isTrusted=false. Do not call it native tab/focus acceptance.

## Layout observation

Screenshots at 360/768/1280/1440 were inspected for the actual component fixture.
URL and route-unique heading are asserted before capture; all widths have no
horizontal document overflow. The fixture imports current global/buyer styles,
but uses a font fallback rather than Next's font loader and has no full Next shell.
These are not production-route or complete responsive acceptance screenshots.

The shared button style could override G4's intended minimum height. A browser
assertion measured 40px against the required 44px:
`.local/social-v2-g4-touch-target-red.log`. One screen-scoped CSS rule now protects
buttons and authorization action links. Final browser checks pass for the load
more control at all four widths and the login action. No other screen CSS changed.

## Actual Next/API and issued-cookie browser proof

Added an opt-in SocialActivityBrowserIntegrationTest and durable browser script
`apps/web/e2e/social-activity-browser.mjs`
to run actual Next dev/webpack, a random-port API, disposable PostgreSQL and real
issued JWT HttpOnly cookies. A source-only web copy excludes .env files, build
outputs and dependency copies; its node_modules junction reuses installed code.
Only isolated child processes enable the feature for the fixture. The normal
web/API processes, recovered data and runtime flags remain untouched.

The browser still uses localhost:3000 as its origin; context-scoped forwarding
targets the isolated Next listener, not the already-running local application.
The initial interceptor closed HMR sockets, and SSR rendered without client
activity requests. Forwarding the isolated server's actual HMR traffic restored
hydration without a production change. The earlier compile error was a wrong test
import for LocalServerPort and was also corrected only in the harness.

With real HTTP and real JWT cookies, the test then exposed a separate layout
failure: the common header extended the 1280px document to **1295px**, clipping the
account action. Captured offenders are header right/actions groups, not the
activity rows. Fixed groups plus the center/search automatic minimum widths
prevented shrinking to the available space. Two scoped min-width:0 declarations
at the existing >=1280 breakpoint fix that demonstrated overflow, retaining all
header contents, links, search and business behavior. No redesign, hidden actions,
overflow clipping or header JSX change was used. RED artifacts:
`.local/social-v2-g4-live-browser-layout-red.log` and matching `.json`.

Final `.local/social-v2-g4-live-browser.log`: **1 test passes, 55.928 s**, no
failures/errors/skips. The single integrated case proves:

1. Real Next SSR/hydration and existing generated client query the random-port
   backend using an actual InitialSessionIssuer JWT in an HttpOnly cookie.
   Playwright installs the issued cookies; this does not test the login form.
2. Actual Next Link navigation selects/restores LIKED/SAVED URL tabs. A owns 21
   liked items (20 first page plus one real continuation) and one saved item;
   B owns a distinct liked item. No client user selector is sent.
3. The proxy holds A's real successful continuation response. Replacing the
   browser cookie with B's issued JWT and dispatching the explicit synthetic focus
   event starts a fresh B request despite the held page. B's row renders, the old
   request is aborted and A's rows/cursor controls are absent after release.
4. Navigating back to `/me` renders B's real server-loaded profile. Its actual
   activity entry opens the route; COMMENTED has the server's empty state.
5. Removing cookies while B's row is displayed produces real API 401 and the
   existing Next SSR-refresh bridge's 401. The authorization view replaces rows;
   no B private row remains. Expected HTTP-401 console diagnostics are recorded,
   with no uncaught page errors.
6. Document scroll width equals viewport width at 360/768/1280/1440. Actual Next
   route screenshots assert the destination URL and exact heading before capture,
   and were visually inspected at all four widths. Unlike the earlier component
   fixture, these include current shell and served font files. Next's development
   indicator is test-runtime chrome, not a production UI asset.

Final JSON/screenshots/Next/browser logs are under
`.local/social-v2-g4-live-browser/`. The fixture token manifest is deleted in
finally; it is ignored by Git and its token values are redacted from browser
failures. Isolated web/browser children are stopped on success/failure. Normal
ports 3000/8081 still have their original owning processes. Relevant tested web
copy hashes match the worktree hook, screen/CSS, route, profile entry and header.
Targeted ESLint for the durable browser helper and changed hook/test also passes
in `.local/social-v2-g4-live-browser-lint.log`. No unchanged full BE or web suites
were rerun to claim this slice.

Reproduction requires a fresh source-only isolated web copy and installed
dependencies (never copy runtime .env/recovered data). In the isolated API copy:

```powershell
.\mvnw.cmd -o '-Dtest=SocialActivityBrowserIntegrationTest' '-Dcovie.test.social-activity-browser=true' '-Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web' '-Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-activity-browser.mjs' '-Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g4-live-browser' test
```

Current gate remains partial: a real account/session change without trusted prop
update or reconciliation event is not proved safe. The real-cookie system case
still uses synthetic focus, not native OS tab activation. Cross-browser behavior,
hidden-item reconciliation without an event, storage/CDN hiding, staging/load and
release gates remain open. G1–G3 remaining technical gates and G5 video are not
closed by this G4 proof.
No broad BE rerun, recovered DB access, service restart, flag activation of the
normal application, commit or deployment occurred. Whole V2 stays active/OFF.
