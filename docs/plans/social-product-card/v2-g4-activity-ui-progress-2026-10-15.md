# Social Product Card 2.0 — G4 activity UI, local checkpoint

## Owner direction and implementation

The owner explicitly removed the requirement to match Figma because the project's
UI has changed. This checkpoint follows the current profile/notification visual
system: existing CoVie tokens, shared buttons/ErrorState, Next Link/Image and
Phosphor icons. No more exact-frame input is needed for this approved scope.
This does not waive browser, privacy, infrastructure or release gates.

- Added `/me/activity`, behind server web flag
  `COVIE_SOCIAL_V2_ACTIVITY_ENABLED === "true"` (OFF by default). The profile entry
  uses that same web flag; existing profile menus stay unchanged when OFF.
- The server route calls existing requireUser and passes only the authenticated
  account identity to the already-tested scoped activity hook. The activity API
  still receives no client user selector. No API/DTO contract was changed.
- URL query `type=LIKED|SAVED|COMMENTED` restores the selected tab; missing,
  unsupported or repeated values default to LIKED. Tab links use Next navigation,
  selected state and panel associations. Arrow/Home/End keys move tab focus;
  Enter follows the focused link (manual activation).
- Rows display thumbnail, literal item/merchant text, action time and literal,
  newline-preserving comment excerpt. Distinct comment IDs on one item remain
  distinct rows. Only the existing `/items/[itemId]` link is used: no unverified
  comment deep-link contract was invented.
- Added loading/skeleton, empty, error/retry, unauthorized and append/loading-more
  presentation. Cursor retry keeps already loaded rows through the existing hook.
  A broken image gets a stable placeholder; unavailable entries are generic and
  do not render the supplied name, merchant, excerpt, image or item link.
- Responsive CSS uses existing project variables, wrapping text and 44px tab/back/
  load-more controls. This is source implementation, not rendered viewport proof.

## Targeted verification

Missing component RED is recorded in `.local/social-v2-g4-screen-red.log`.
Initial route/screen/profile checks: **8 passes, 5.01 s**, in
`.local/social-v2-g4-screen-final.log`. After adding the unavailable/image fallback
case, the final screen-only run records **3 passes, 5.37 s** in
`.local/social-v2-g4-screen-render-final.log`. The unchanged route/profile checks
were not repeated after this screen-only addition.

These checks cover literal HTML-looking excerpt text, comment identity, URL tab
links and keyboard focus, append failure/retry retaining rows, empty/403 states,
image failure/unavailable privacy presentation, default-OFF route/profile entry,
trusted server actor/type props and the enabled profile entry. Existing profile
edit/account-switch checks pass alongside the changed entry component.

Final web typecheck and targeted ESLint exit zero:
`.local/social-v2-g4-screen-typecheck.log` and
`.local/social-v2-g4-screen-lint.log`. The initial typecheck found an optional mock
call access in the new test; only that assertion was corrected. No existing
production behavior was changed to accommodate a test.

Tests render React and mock transport/auth/navigation. They are not real Next
navigation, issued-cookie browser acceptance, screenshot/viewport verification or
an end-to-end backend run. No native-browser UI acceptance is claimed.

## Open gates and deployment status

G4 visible UI is now implemented locally, superseding the earlier data-only
checkpoint. Real account switching with stale trusted props/cookies, cross-tab
reconciliation races, real route navigation and viewport checks remain unproved.
The hook's existing scoped mocked tests are not full privacy-gate evidence.

No runtime flags/environment, recovered data, client generation, authentication
infrastructure, process restart, commit or deployment changed. Other G1–G3 gates,
video G5 and staging/release G6 remain open. All V2 activation stays OFF; no
staging environment has been supplied. The full objective remains active.
