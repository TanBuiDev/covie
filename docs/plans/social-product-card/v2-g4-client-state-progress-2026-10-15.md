# Social Product Card 2.0 — G4 client/data-state checkpoint, UI unfinished

## Design prerequisite and unchanged scope

Inspected the current private profile implementation, server `requireUser`
pattern, existing UI tokens/components and Figma Buyer Mobile metadata (`3:4`).
Relevant metadata includes public merchant profile and Trust screens, but no
verified **private `/me` profile or `/me/activity` production frame** was located
in this inspection. This is not a claim that no such frame exists anywhere in
the file. The public merchant profile is not a substitute for private activity.

Asked asynchronously for the intended exact frame link. This asks for missing
design evidence, **not another plan/business approval**. All previous approvals
persist. No route or profile UI was edited against a guessed frame. The intended
G4 UI/profile entry/URL tabs remain part of the objective, not replaced by this
data-only checkpoint. Other backend technical gates can progress in parallel
with waiting for design information; the whole goal is not at an impasse.

## Implemented preparation

- `social-activity-api.ts` consumes generated query/DTO types and the generated
  private GET. It sends only type, limit 20 and optional opaque cursor, never
  a user selector; requests include credentials, no-store and optional abort.
  The existing parsed-error helper retains authentication error codes after the
  generated transport has consumed JSON.
- `use-social-activity.ts` scopes in-memory rows/cursor to a trusted account ID,
  activity type and enabled state. Account/tab scope changes reset private data;
  cleanup aborts transport and ignores old completions.
- Append keeps prior rows; an immediate pending guard prevents competing load
  more calls. Retry keeps the failed page's cursor and confirmed previous rows.
  Deduplication uses comment ID for COMMENTED, so distinct comments on one item
  are preserved, versus item ID for reaction lists.
- 401/403 clear all private rows and continuation state. Disabled/missing-account
  state makes no request. No activity data enters localStorage or a global cache.
- Focus/online/visible-tab events request a fresh first page via the same scoped
  path, instead of treating current-state activity as an immutable audit log.
  This listener behavior is source implementation, not a browser acceptance gate.

The hook is not yet mounted by any activity route or profile view. No new flag
was enabled and no user-facing feature completion is claimed.

## Verification

`.local/social-v2-g4-client-state-red.log`: the two new test files fail to import
the missing adapter/hook before implementation. The initial post-implementation
five cases pass; final strengthened checks are in
`.local/social-v2-g4-client-state-final.log`: **6 passes, 1.62 s**.

Cases prove:

1. Generated GET query/credentials/no-store/abort and no user selector.
2. Already-parsed 401 response keeps its exact authentication error code.
3. Append/retry uses the same cursor, prevents double load-more and deduplicates
   an overlapping comment while retaining a different comment on the same item.
4. Combined account/type scope change clears old data; an old pending page
   completion cannot overwrite the new scope.
5. Each of 401 and 403 clears loaded private rows/cursor; disabled state does not
   fetch.

Web typecheck and targeted ESLint exit zero, with no lint output:
`.local/social-v2-g4-client-state-typecheck.log` and
`.local/social-v2-g4-client-state-lint.log`. Tests mock network responses and
render the hook, not a visible route, real session or native browser.

No BE tests or unchanged client generation were repeated. No existing profile,
auth/session, route, schema, recovered-data, runtime-environment or process change,
restart, activation, commit or deployment occurred.

## Remaining privacy/UI gates

The eventual server page must pass the authenticated account identity and update
it on account changes. The hook does not independently prove the response actor:
cookies changing in another tab while a stale account prop remains are **not**
covered by the scoped mocked tests. That real account-switch race needs evidence
and a justified solution before the feature is activated; no broad authentication
refactor or extra API fields were invented here.

UI remains unfinished: exact frame, `/me/activity`, profile entry, URL-persistent
three tabs, safe literal excerpt/thumbnail/time presentation, keyboard semantics,
loading/error/empty/unauthorized states and rendered append/retry acceptance.
Real browser focus reconciliation and responsive checks also remain open.
Other G1–G3 gates, G5 video and G6 staging/release evidence are unchanged. No
staging exists. Flags stay OFF and the full objective stays active.
