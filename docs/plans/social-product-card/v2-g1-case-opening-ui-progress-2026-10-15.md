# Social Product Card 2.0 — G1 case opening UI

Local implementation only, on the existing dirty working tree. The approved
contract is `v2-g1-admin-case-opening-contract.md`; all separate approvals in the
V2 plan are already granted by the owner. Safe activation gates still apply.

## Implemented

- Admin listings passes a separate server-side
  `COVIE_SOCIAL_V2_ADMIN_CASE_OPENING_ENABLED` switch to the screen. Unless its
  value is exactly `true`, the opening form is absent. The existing read switch
  remains independent. No runtime environment configuration was changed.
- The detail moderation section offers reason (required, max 60), optional note
  (max 2000) and explicit confirmation that opening does not change item status.
  Editing either field clears confirmation. Pending requests disable inputs and
  submit; the server remains authoritative about eligibility and case reuse.
- The API helper consumes generated AdminReasonRequest and
  AdminCatalogCaseOpenedResponse types through the existing CSRF-aware browser
  client. It sends Idempotency-Key, retains the same key for a same-body retry,
  and validates the returned actual UUID/created result before navigation.
- Success navigates to the returned `/admin/moderation/{caseId}`, including when
  the backend reuses an OPEN case. No fake case or successful visibility change
  is inferred. HIDE/RESTORE remain on the existing case workflow.
- A 409 explains the conflict and reloads authoritative listing/detail/cases.
  A 401/403 clears displayed privileged data. Completion from an unmounted form
  cannot navigate away from a subsequently selected item.
- Reuses Admin fields/buttons/tokens and the existing detail section; no new
  icon/static asset or redesigned sidebar. Minimum control targets are 44px.
  Exact Figma frame `399:1413`, file `4LcMXFQz3BnU1EXzBk69t2`, was reviewed using
  the retained high-fidelity context and screenshot. Actual rendered comparison
  remains unverified; this is not a visual-completion claim.

## Verification

| Check | Actual evidence |
| --- | --- |
| RED | `.local/social-v2-g1-case-opening-ui-red.log`: new opening control missing; 2 fail, existing 5 pass |
| Final component tests | `.local/social-v2-g1-case-opening-ui-final.log`: 7 pass, 4.84 s |
| Web typecheck | `.local/social-v2-g1-case-opening-ui-typecheck.log`: exit 0 after correcting test-only optional-array indexing |
| Targeted ESLint | `.local/social-v2-g1-case-opening-ui-lint.log`: exit 0 |

The two added component tests exercise reason/confirmation, transient failure and
same-key retry, reused-case navigation, and conflict-triggered reload of real
response data. Existing no-case verification also checks the default-OFF form is
absent. Transport/router are mocked: no live browser, actual CSRF cookie or
end-to-end web-to-backend success is claimed. Backend/API contract is unchanged in
this step; previously passed backend gates were not rerun.

## Remaining

G1 is still partial: actual web-to-backend/browser evidence at 360/768/1280/1440,
query-plan validation and object storage privacy remain
unverified. G2 caption/media integration, G3 feed preview/gallery, G4 activity,
G5 verified video and G6 operational/release gates remain in the original scope.
No staging exists. Whole V2 objective stays active and features stay OFF; no
recovered-data writes, original service restart, commits or deployments occurred.

The subsequent two-Admin opening race is verified separately in
`v2-g1-case-opening-progress-2026-10-15.md`; it is backend concurrency evidence,
not browser evidence.
