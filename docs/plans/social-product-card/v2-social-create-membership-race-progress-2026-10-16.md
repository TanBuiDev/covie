# Social V2 — permission revocation during caption Create

## Source gap and reproduced defect

Plan section 8.3 requires necessary in-transaction authorization checks when Seller permission changes during a command. The earlier PATCH repair does not cover `CatalogCreateService.createInTransaction`, which still used a plain ACTIVE membership read before its existing merchant row lock. Existing Create revocation tests prove the next-request/replay denial, not an in-flight Create.

New `SocialCreateMembershipRevocationIntegrationTest#committedRevocationCannotBeFollowedByWaitingCaptionCreate` uses actual issued OWNER/MANAGER JWTs, actual membership DELETE and Create MockMvc endpoints, disposable PostgreSQL, and no business/repository mocks. The test-only pool size 6 accommodates two independent blockers, two workers and an observer.

Controlled schedule:

1. Hold independent membership and merchant row locks.
2. Start actual OWNER membership revocation and observe its UPDATE waiting.
3. Start MANAGER Create with caption, READY image and a valid 25000 VND offer; observe its merchant SELECT FOR UPDATE waiting too.
4. Release only membership blocker; await actual revocation 204 and persisted REVOKED membership, with zero merchant items at that point.
5. Release merchant blocker and inspect the Create result and persistence.

Actual RED: Create returned **201** and persisted **one item** after completed revocation, rather than 404. `.local/social-v2-create-revocation-red.log`, Maven 34.965 s. This is local isolated evidence, not exposure evidence from the normal running services.

## Minimal fix

Only `CatalogCreateService.createInTransaction` changes. When caption authoring is enabled and caption or media IDs are supplied, it uses the already-established `access.requireForMutation` shared membership lock. Otherwise it retains the original `access.require` path.

The lock runs inside the existing IdempotencyExecutor transaction. A revocation queued first completes before the ACTIVE membership locking predicate is evaluated, so Create fails with existing 404 RESOURCE_NOT_FOUND and the transaction rolls back its in-progress idempotency record. Existing pre-idempotency authorization/activity checks, replay handling, merchant ACTIVE check/lock, limits, pricing, inventory and outbox remain unchanged. No API/schema/client/migration or shared access-policy change in this checkpoint.

## Targeted verification

Sequential isolated API copy, source-synced production file, disposable PostgreSQL:

```text
mvnw.cmd -o -Dtest=SocialCreateMembershipRevocationIntegrationTest,MerchantCatalogCreateControllerIntegrationTest#createsCaptionForProductServiceAndResaleWithExactReplayAndUnchangedCommerce+revokedIssuedOwnerJwtCannotReplayOrCreateCaptionContent test
```

- `.local/social-v2-create-revocation-green.log`: **3 PASS**, Maven 44.088 s.
- Actual lock waiters become membership UPDATE plus SELECT ACTIVE membership FOR SHARE.
- Actual revocation 204, waiting Create 404, persisted items 0.
- Merchant/location/media all-column snapshots unchanged; zero item/media association/location offer/Create idempotency record/CATALOG_ITEM_CREATED event; exactly one membership revocation audit. Same issued manager JWT/same-key retry 404.
- Existing PRODUCT/SERVICE/RESALE caption Create, exact replay, UTF-16 limit, changed-body key conflict and post-revocation replay/new-key denial remain passing.

Because Create code changed, separately check its affected media-only and flag-OFF paths (not the unchanged PATCH races):

```text
mvnw.cmd -o -Dtest=CatalogCaptionFeatureDisabledIntegrationTest,MerchantCatalogCreateControllerIntegrationTest#v2CreateLimitsDistinctMediaAndPersistsRequestedOrderAndCover test
```

- `.local/social-v2-create-membership-guard-regression.log`: **2 PASS**, Maven 27.588 s.
- Includes media-only cap rejection with rollback and corrected same-key ordered/cover success, plus existing flag-OFF caption denial and legacy Create/patch behavior.
- Source/private Create service SHA-256 match: `F3E64A65C5FC455EE50CDFA5B208C8871819686F4318E0CDE779064DCE43B61F`.
- Targeted diff check passes; normal web/API PIDs 7544/25008 remain alive. No full BE rerun, recovered/runtime DB access, restart, activation, Git remote/branch/commit operation or deployment.

## Limits and next action

This does not prove the opposite Create-first revocation ordering, role downgrade, merchant-state contention, every legacy flag-OFF race, browser behavior, load or staging. The existing merchant ACTIVE check is preserved, not redefined to invent editing restrictions. Whole V2 remains ACTIVE; normal V2/video flags remain OFF and release/staging unverified.

Next: reconcile the remaining G1–G4 gates against current source and recorded evidence, separating actual missing implementation from local verification and unavailable staging/storage infrastructure. Do not repeat passing unchanged checks or begin G5 video before its actual G1–G3 prerequisites pass.
