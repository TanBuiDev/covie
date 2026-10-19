# Social V2 — permission revocation during caption/media PATCH

## Requirement and source evidence

Plan section 8.3 requires a necessary in-transaction permission recheck when Seller permission changes during a command. Existing `issuedCaptionJwtRespectsMerchantScopeStaffCapabilityAndMembershipRevocation` proves rejection on the next request after revocation, not an already-running transaction.

`CatalogEditService.updateItem` previously called the plain-read `MerchantAccessPolicy.require` once before loading/mutating the item. A concurrent membership revocation could therefore commit after that read and before the actual Social item update.

## Actual failing HTTP schedule

New `SocialEditMembershipRevocationIntegrationTest#committedRevocationCannotBeFollowedByWaitingCaptionAndMediaWrite` uses issued OWNER and MANAGER JWTs, actual MockMvc membership DELETE and catalog PATCH endpoints, disposable PostgreSQL, and no business/repository mocks.

Two independent database transactions block the manager membership and catalog item. The actual OWNER revocation starts first; pg_stat_activity observes its membership UPDATE waiting. The manager's caption+replacement-media PATCH then starts, and the observer sees its item UPDATE waiting too. Release only the membership blocker, await actual revocation 204, read persisted REVOKED membership and unchanged item, then release the item blocker.

Before the fix, the waiting PATCH returned 204 instead of 404 after the completed revocation. Evidence: `.local/social-v2-edit-revocation-confirmed-red.log`, 1 failing assertion, Maven 33.780 s. This is a local reproduced authorization race, not a claim that normal running services exposed it.

The first harness attempt exhausted its test profile's four-connection pool (two blockers, two workers, no observer connection). Only this new test sets maximum pool size 6. `.local/social-v2-edit-revocation-red.log` records that harness error; it is not product defect evidence.

## Minimal production change

- `MerchantMembershipRepository.findActiveForMutation`: pessimistic READ of the same actor/merchant/ACTIVE membership predicate; actual PostgreSQL SQL is `FOR SHARE`.
- `MerchantAccessPolicy.requireForMutation`: selects that locking read and runs exactly the existing role/capability/assignment validation. Existing `require` continues with its original nonlocking read through the same validation body.
- `CatalogEditService.updateItem`: uses the new guard only when the caption authoring flag is ON and caption or media IDs are supplied. Unrelated item fields, variants, offers, reads and flag-OFF requests retain the old access path.

The shared membership lock lasts in the existing edit transaction. A revocation already queued first completes before the edit's locked predicate is evaluated; a revoked membership is no longer selected and the edit returns the existing 404 RESOURCE_NOT_FOUND. No JWT, roles, API schema, status/error code, migration, DTO, generated client or transaction isolation change.

## Targeted verification

Sequential runs in `.local/social-v2-api-validation/api`, source-synced files, disposable PostgreSQL:

```text
mvnw.cmd -o -Dtest=SocialEditMembershipRevocationIntegrationTest,MerchantCatalogEditControllerIntegrationTest#issuedCaptionJwtRespectsMerchantScopeStaffCapabilityAndMembershipRevocation+v2MediaEditPreservesUnchangedLegacyGalleryAndEnforcesNewOrderingLimit test
```

- `.local/social-v2-edit-revocation-green.log`: 3 PASS, Maven 40.698 s.
- Actual waiters change from membership UPDATE plus item UPDATE (RED) to membership UPDATE plus SELECT ACTIVE membership FOR SHARE (GREEN).
- Actual revocation 204 then waiting PATCH 404. Full item, gallery and owned asset rows unchanged; zero item moderation cases; one membership revocation audit; same issued manager JWT retry 404.
- Existing MANAGER success, foreign merchant denial, STAFF denial, next-request revocation, caption-only/identical legacy gallery preservation and new ordered media limits remain passing.

Since the edit guard changed, proportionate regression rechecks the affected edit/moderation contention and flag-OFF path, not the full BE suite:

```text
mvnw.cmd -o -Dtest=CatalogCaptionFeatureDisabledIntegrationTest,SocialEditHideConcurrencyIntegrationTest,SocialEditRestoreConcurrencyIntegrationTest test
```

- `.local/social-v2-edit-membership-guard-regression.log`: 3 PASS, Maven 30.231 s; includes two edit/HIDE and four edit/RESTORE schedules plus legacy flag-OFF assertions.
- Source/private-copy SHA-256 matches for all three production files; targeted diff check passes.
- Normal web/API PIDs 7544/25008 remain alive. No recovered/runtime database access, runtime restart, deployment, activation, remote Git operation or full BE rerun.

## Remaining scope

This does not prove the opposite edit-first/revocation ordering, concurrent role downgrade, merchant suspension, caption Create transactions, native browser behavior, load, or staging. It does not change existing merchant-state editing semantics by inference. Whole V2 remains ACTIVE; normal V2/video flags OFF and staging/release unverified.

Next source audit: `CatalogCreateService.createInTransaction` also uses a plain membership read before its merchant lock. Inspect and, if needed, reproduce that separate caption Create/revocation schedule before making any further fix.
