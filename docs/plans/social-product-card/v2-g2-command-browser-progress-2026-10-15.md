# G2: independent offer command versus dirty Social draft

## Actual browser evidence

Source review showed that an independent command calls `router.refresh()` and
the real page revision key includes offer versions. An accepted offer update
can therefore remount the Editor and discard unsaved Social state. Component
tests had covered the confirmation guard, but not this actual Next refresh.

The new `apps/web/e2e/catalog-social-command-browser.mjs` uses actual isolated
Next, API, disposable PostgreSQL, issued HttpOnly OWNER cookie and the normal
CSRF/typed-client path. There is no mocked command or router refresh.

Starting from the fixture's DRAFT item with stored caption `Persisted caption`
and price 25000, the browser enters a different multiline/literal caption,
selects a local gallery file and changes the offer price to 31000:

1. Decline the real confirmation: caption, gallery dirty state and entered price
   remain; no API write occurred.
2. Accept the second real confirmation: exactly one location-offer PATCH returns
   204. Its body changes the price without caption or media IDs. The actual Next
   refresh restores the stored caption and clears discarded caption/gallery
   dirty state, while the authoritative offer price is 31000.
3. Reload: the stored caption and price remain correct, with no further write.

The Java fixture's opt-in expected-offer-price branch independently verifies the
database: all-column JSONB of items, variants and gallery links is unchanged;
offer fields excluding price/version/update timestamp are unchanged, exact price
is 31000 and version is 1. No owner media asset was created. Without the explicit
test property, the fixture retains its previous full read-only snapshot check.
The existing fixture class/method name still mentions native navigation; this
run used the new command script override, not the earlier navigation sequence.

## Result and limits

- One focused integration test PASS, 51.50 seconds, Maven 01:06, exit 0:
  `.local/social-v2-g2-command-browser.log`.
- Redacted browser evidence and two screenshots:
  `.local/social-v2-g2-command-browser/`.
- Two actual confirm dialogs (decline/accept), one PATCH 204, no uncaught page
  errors. Targeted script lint PASS:
  `.local/social-v2-g2-command-browser-lint.log`.
- The cancel screenshot was inspected; its viewport is scrolled to the offer
  panel, not a complete visual capture of the caption. Draft preservation is
  established by browser value/dirty-state assertions, not that screenshot.

Existing command/discard/refresh production behavior passed unchanged; no fix
or refactor was justified. The two form hydration corrections from the preceding
checkpoint were present in this run. This does not independently prove variant,
lifecycle, conflict/revocation, Firefox/Safari or every unsupported navigation
path. Unchanged component suites and the prior navigation/upload sequences were
not rerun. Other G1/G3/privacy/load/staging gates remain open.

All writes were confined to disposable fixture data. No recovered-data access,
normal process restart, runtime migration, flag activation, remote change or
deployment occurred. Normal V2/video flags stay OFF; staging/release remain
unverified and the whole objective stays active. Current project UI, not Figma
matching, remains the owner's approved visual reference.

Next action: Audit the remaining Admin catalog list/detail query paths against the approved G1 technical gates.
Required from you: Nothing
Completion condition: Identify the exact unverified query evidence without rerunning passed history-index or unchanged feature tests.
