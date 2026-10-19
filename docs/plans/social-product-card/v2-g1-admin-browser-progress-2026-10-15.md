# G1: actual Admin browser flow and bounded table corrections

## Real integration, not mocked transport

New opt-in `AdminCatalogBrowserIntegrationTest` starts the isolated source-copy
Next dev/webpack against the random-port real API and disposable PostgreSQL.
It issues an actual Admin session and installs its HttpOnly cookie; login/MFA
forms are not part of this test. Playwright Chrome 155 uses the required
`http://localhost:3000` origin, forwarding real HTTP/HMR traffic to the owned
isolated Next child. API responses and navigation are not mocked. Normal 3000/
8081 services are not used or restarted.

The fixture has 21 ACTIVE/APPROVED PRODUCT/STANDARD items, one actual multiline
caption and one PENDING_UPLOAD image association. This is synthetic seed data,
not evidence of a Seller publish/upload chain or READY asset delivery.

`e2e/admin-catalog-browser.mjs` verifies:

- Real list page 0 returns 20 rows; load-more appends the remaining row, retains
  the first item and has 21 distinct names. Caption presence comes from real
  summary booleans (one true, remaining false).
- A real nonmatching query gives an empty state. Combined ID search, merchant,
  type, listing kind, listing/moderation status and CREATED_ASC filtering gives
  exactly one authoritative row, with actual query parameters in the URL.
- The detail action is reachable at 360px, including horizontal table scrolling.
  Description remains separate from the exact literal multiline caption; no
  script element is rendered. Pending media has no leaked fixture storage key
  and renders its actual not-ready status/placeholder, not a fabricated image.
- The initial no-case state is real. Reason/note without confirmation leaves the
  submit button disabled. Explicit confirmation performs exactly one actual
  POST with an Idempotency-Key and normal CSRF/client flow, returning 200 and
  `created=true`. The browser navigates to the returned case and displays the
  target ID and reason. Returning to the item reveals the actual case link;
  following it opens that same case, not an invented destination.
- Java independently confirms exactly one OPEN SYSTEM_FLAG with SPAM and the
  exact note, one opening audit row and one outbox event. Full-column canonical
  JSONB snapshots of all fixture catalog items, links and assets remain unchanged.
  Opening the case does not hide/publish the item or transition pending media.

## Rendered defects and minimal fixes

The first run reached list/append/empty filter but then used an overly exact
label-text selector on a wrapping label containing select options. That was a
harness error, not proof of a filtering defect; native named selects are now
targeted directly. A redundant attempted private-copy schema path did not exist;
the private web resolves the actual workspace API client through its existing
junction, so no generated schema copy was needed.

Visual review exposed real local table problems, each separately evidenced before
the corresponding production correction:

1. The short `Xem` label wrapped into three lines at 360px (two at 1280px).
   The new browser text-range assertion fails in
   `.local/social-v2-g1-admin-browser-label-red.log`.
   Only `.table button` gains `white-space: nowrap`.
2. With that correction, a 542px intrinsic table still compressed merchant/status
   columns into individual letters at mobile width. A new width/readability
   assertion fails in `.local/social-v2-g1-admin-browser-table-red.log`.
   Only `.table` gains `min-width: 760px`, retaining the existing horizontal
   scroll container and document-width containment. No global styles, shell,
   table content, API or business logic is changed.

The earlier functional GREEN is not substituted for the final responsive run.
Final result: **one integration test PASS, 47.87 seconds; Maven 51.182 seconds,
exit 0**, `.local/social-v2-g1-admin-browser-final.log`. All four list label
measurements are one line; document widths equal 360/768/1280/1440 for both
list and detail. The narrow table is 760px inside its own scroll area; desktop
tables are 930/1090px. No uncaught page error occurred. Script lint and whitespace
check PASS. Tested TSX/CSS hashes match worktree sources.

Evidence and eight screenshots: `.local/social-v2-g1-admin-browser-final/`.
All were visually inspected. Mobile list captures show the existing shell/header
and upper filters, not a full table; mobile detail captures show the scrolled
table action and literal caption area. This is contextual responsive evidence,
not a pixel-perfect design claim, full mobile shell acceptance, screen-reader
audit or complete keyboard/touch coverage. The current project UI remains the
reference; no Figma matching or redesign was attempted.

## Isolation and remaining requirements

Owned Next/browser children stop; the token manifest is deleted in finally.
Normal web/API PIDs remain unchanged. No recovered/runtime DB query or migration,
normal flag activation, remote repository change or deployment occurred. Only
the isolated child enables the Admin read/opening flags. No unchanged full BE,
component suite, client generation/typecheck, query-plan or concurrency fixture
was rerun; retries targeted the observed browser/harness/layout evidence above.

This advances real Admin list/filter/detail/opening/case-navigation proof, not all
G1. Actual error/retry, role revocation, conflict and long-history browser cases,
other moderation races, object/CDN privacy, cross-browser/accessibility/load and
staging/release evidence remain unverified. No staging exists; all normal V2/video
flags remain OFF and the whole Social V2 objective remains active.

Next action: Audit the actual Social feed route/fixtures and prepare its missing real-browser card/preview verification.
Required from you: Nothing
Completion condition: A bounded browser scenario targets the real Social-only batch/card integration without repeating achieved Admin gates.
