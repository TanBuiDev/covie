# Social edit versus Admin hide: real transaction contention

## Requirement and evidence scope

Approved plan 8.3 explicitly requires Seller caption/media edit versus Admin hide
consistency. The earlier sequential edit/moderation tests and concurrent Admin
case-opening pair were not evidence for this different race.

New `SocialEditHideConcurrencyIntegrationTest` performs actual issued OWNER and
Admin JWT MockMvc requests against disposable PostgreSQL. This is the real
servlet/auth/service/transaction path, not a network-wire browser or staging run.
There are no mocked business decisions, permission checks or repository results.
The fixture is an ACTIVE/APPROVED PRODUCT with merchant PRE_APPROVAL policy,
existing SYSTEM_FLAG case, old caption, two READY gallery links and a third owned
READY replacement asset. Offers/variant/description start as actual persisted
commerce data. Storage object delivery is outside this test.

An independent `FOR NO KEY UPDATE` transaction holds the item while the two
commands start in controlled order. The test first observes one, then BOTH actual
versioned item UPDATE statements waiting on PostgreSQL locks before releasing
the blocker. Public preview is read while both are waiting: it returns exactly
the old caption and both original ordered media IDs, never uncommitted new data.
The two workers have separate transactions; the test itself is not wrapped in
one shared rollback transaction.

## Both committed outcomes actually exercised

The single focused test runs two bounded schedules, not a collection of
hypothetical cases:

| First waiting command | Seller / Admin status | Final item |
| --- | --- | --- |
| Seller edit | 204 / 409 | PENDING, new caption, replacement/old-first gallery |
| Admin hide | 409 / 200 | HIDDEN, old caption, exact original gallery |

Both outcomes finish at item version 1. Each conflict returns
`RESOURCE_VERSION_CONFLICT`, not a fabricated success or generic 500. The
schedule assertion is coverage of both observed commit orders, not a new business
rule promising one actor priority in production.

- Seller wins: one OPEN PRE_APPROVAL and one corresponding opening outbox row;
  original SYSTEM_FLAG remains OPEN. No HIDE action, HIDE audit or action-applied
  outbox row is committed by the losing Admin transaction. New gallery IDs, order
  0/1 and primary flags true/false are exact.
- Admin wins: SYSTEM_FLAG is RESOLVED, one HIDE action, one audit and one
  action-applied outbox row. No PRE_APPROVAL/opening-outbox side effect from the
  losing edit survives; all original gallery-row columns are unchanged.
- In both: every other item column (including listing/description), all offer
  columns, the variant row and all owned asset-row JSONB remain unchanged.
  After commit, the public batch omits the item and public item detail returns
  404 for valid coordinates. Thus neither committed outcome exposes the new
  caption/gallery publicly.

This establishes those before/after public observations and write atomicity for
these contended commands. It does not simulate a public response already in
flight across commit, cache/CDN propagation, permission revocation, every catalog
type/state/policy, notification dispatch or all moderation races.

## Targeted result

The initial focused run passed. The test was then strengthened to compare every
pre-commit preview media ID, all asset snapshots and a positive final version;
only that changed test was rerun. **Final one test PASS, 20.08 seconds; Maven
31.758 seconds, exit 0**, `.local/social-v2-edit-hide-race-final.log`.

Existing production optimistic versioning and transaction rollback passed; no
lock refactor, business fix, index, schema, API or generated-client change was
justified. The test uses the existing documented 409 contract. Whitespace check
PASS; the isolated `CatalogEditService` hash matches worktree source.

No old case-opening/visibility/browser/component/full-BE suite, client generation
or typecheck was repeated. All fixture writes and contention were in disposable
PostgreSQL; no recovered/runtime DB access, normal process restart, runtime
migration, flag activation, remote change or deployment occurred. Normal web/API
PIDs remain unchanged; normal V2/video flags remain OFF. No staging exists, and
the whole objective remains active.

## Next independent moderation requirement

Plan 8.3 separately names Admin RESTORE after caption/media changes. Current
source keeps HIDDEN out of the edit recheck branch, while `restoreVisibility`
turns HIDDEN/REJECTED into APPROVED. That is a concrete reason for a separate
actual policy/revision check, not proof yet of a reproduced bypass or authority
to redesign existing restore behavior. The successful edit/hide race must not be
repeated as a substitute for this different requirement.

Next action: Reproduce the actual hide→Seller Social edit→Admin restore sequence under PRE_APPROVAL policy and inspect public visibility.
Required from you: Nothing
Completion condition: Verify whether restore can publish modified, unapproved Social content; propose only the smallest policy-compatible correction if the real sequence fails.
