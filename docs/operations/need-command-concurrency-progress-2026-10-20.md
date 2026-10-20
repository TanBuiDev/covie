# Need command contention acceptance — 2026-10-20

Scope: explicit original Plan06 Task20 command/concurrency requirements, isolated checkout only. Normal local databases, flags, credentials, payment providers and services were not used or changed. The existing Testcontainers PostgreSQL18 fixture and backend-issued test JWTs exercise real MVC/security/service/JPA/SQL transactions; no mocked Need/Order repositories or outbox writers are used by the six new cases.

## Concrete defect and minimal repair

The first focused six-case run actually failed response replacement with HTTP500. PostgreSQL reported `ux_need_response_active_location_variant`: the replacement retained an existing LocationVariant, Hibernate scheduled the new ACTIVE INSERT before the old item's RETIRED UPDATE. This is not an inferred concurrency fault or an invalid request: retaining an eligible offer during response replacement is valid. The transaction rolled back.

Minimal production change: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseService.java` flushes retired item updates immediately after `active.forEach(item -> item.retire(now))`, before creating replacement ACTIVE rows. Same transaction, history, optimistic version, API and policy remain unchanged. No applied migration/SQL/index or business requirement changed.

The first expiration fixture also failed, but for a test-only reason: it moved expires_at earlier than created_at and violated `ck_need_expiry_after_create`. Correcting the disposable fixture's created_at to an hour earlier preserves the real schema invariant. It is not labeled a product failure.

## Actual run receipts

| Run | Actual result | Scope |
|---|---|---|
| `.local/need-explicit-command-races-20261020.log` |6 reported,4 passed,1 failed,1 error,0 skipped; Maven47.708s, exit1 | Product replacement500 plus invalid expiration-fixture timestamp. Not a green run. |
| `.local/need-explicit-command-races-fixed-20261020.log` |2 passed,0 failures/errors/skips,21.17s; Maven41.368s, exit0 | Only the two previously failing cases after one-line production flush and fixture timestamp correction. Do not synthesize an aggregate six-case green. |
| `.local/need-command-regression-final-20261020.log` |**68 passed,0 failures/errors/skips,36.07s; Maven51.527s, exit0** | Whole affected `NeedEligibleItemsControllerIntegrationTest` once, justified by changed response replacement transaction. Includes final deterministic winner assertions and existing Need regression paths. No full-BE rerun. |
| `.local/need-original-query-plans-20261020.log` |1 passed,0 failures/errors/skips,44.92s; Maven49.519s, exit0 | Separate query worker's `NeedOriginalQueryPlanIntegrationTest`; its nine actual SQL/bind/JSON plans are interpreted in that worker's separate report, not added to68 as an invented single suite. |
| `.local/need-expiry-both-orderings-final-20261020.log` |**1 passed,0 failures/errors/skips,26.70s; Maven44.292s, exit0** | Only the changed expiry method after adding deterministic actual scheduler-winner and before-deadline confirmation-winner branches. Earlier68-case gate remains its recorded snapshot, not falsely rerun or counted69. |

Final affected-class gate: `.local/need-command-final-clock-isolation-20261020.log` records **68 passed,0 failures/errors/skips**,37.84s; Maven41.844s, exit0 at21:31:02+07. The class-scoped Primary thread-local test clock warranted this final run to verify default-clock isolation and all final assertions; this is not an aggregate of earlier runs.

All Maven invocations were sequential. No `NeedResponseServiceTest` class exists; no invented class or broad wildcard run was added. Post-test Docker inventory retains only the user's existing compose-postgres/redis/minio containers; Testcontainers-owned resources are gone.

## Exact new case coverage

All six methods are in `apps/api/src/test/java/vn/covie/needs/api/NeedEligibleItemsControllerIntegrationTest.java`.

| Method | Required real assertions | Limit |
|---|---|---|
| `concurrentNeedResponseCreationKeepsOneActiveResponseAndItemSet` | Two actual issued-JWT response-create requests are blocked on the same Need row; pg_stat_activity observes two lock waiters before release. Exactly201/409 with NEED_RESPONSE_ALREADY_ACTIVE, one ACTIVE response/item set, one persisted NEED_RESPONSE_CREATED event. | One serialized winner suffices for uniqueness; not high-throughput load/soak. |
| `concurrentNeedCancellationAndResponseCreationPreserveSerializedHistory` | Both fixed orderings are executed. Cancel-first gives cancel200/create409 NEED_NOT_ACTIVE/no response. Response-first gives create201/cancel200 and retained response history. Need CANCELLED, exact one cancellation and zero/one creation outbox according to winner. | Test-only outer transaction holds the first real command's Need lock until the second actual command is observed waiting in PostgreSQL. No FIFO assumption or fake database response. |
| `concurrentNeedReplacementAndCheckoutKeepHistoricalSourceOrRejectChangedPreview` | Both fixed orderings after actual response/Preview. Replace-first causes old source RETIRED and Commit409 CHECKOUT_CHANGED/no Order. Commit-first creates the Order, replacement200 retires source without removing it, exact Order source FK remains valid. One response-update event. | Same bounded real-transaction scheduling; no independent Buyer UI/browser/performance claim. |
| `concurrentExpiredNeedClaimAndActualOrderConfirmationNeverResurrectNeed` | Three actual cases: (1) expired logical deadline, confirmation waits on Need, scheduler SKIP LOCKED claims0, confirmation then commits CONFIRMED/Need EXPIRED/one EXPIRED event; (2) real scheduler expiration transaction wins and holds Need until actual confirmation is observed waiting, then Order confirms without resurrecting Need, one EXPIRED/zero FULFILLED; (3) confirmation locks before the explicit deadline and holds FULFILLED until real scheduler evaluates after the deadline and skips its lock, one FULFILLED/zero EXPIRED. All Orders use actual response→Preview→Commit and final persisted Order state is CONFIRMED. | Test-only thread-local AppClock sets exact before/after deadline per contender; unset paths delegate Instant.now. Actual PostgreSQL locks and actual scheduler/HTTP commands remain unchanged. No wall-clock sleeps determine deadline winner, no real environment clock or production configuration changed. |
| `concurrentActualSourcedOrderConfirmationsCommitOneNeedFulfillmentEvent` | Two actual sourced Orders created via separate Preview/Commit calls, both actual confirmation requests wait on Need, both return200 and persisted Orders CONFIRMED. Need FULFILLED, winning OrderID one of those two, exactly one real persisted NEED_FULFILLED event. | Does not claim a deterministic FIFO first-waiter rule; actual transaction winner is allowed. This supersedes the older participant-only mocked-source/outbox proof for this command scope. |
| `actualSourcedOrderRejectionLeavesNeedActiveAndRetainsAttribution` | Actual response→Preview→Commit then Seller reject200; persisted Order REJECTED_BY_SELLER with same source_need_request_id, Need ACTIVE/null fulfilledOrder and no fulfillment event. | Starts from existing valid Need/offer fixture, not a newly-created Buyer Need UI flow. |

Concurrency helpers have bounded15/20-second waits and release owned blockers/latches in finally paths. The same class disables outbox polling and retention for deterministic fixture isolation; actual outbox persistence is still asserted, no delivery/dispatch claim is added.

## Final source fingerprints and import scope

| File | SHA256 |
|---|---|
| `apps/api/src/main/java/vn/covie/needs/application/NeedResponseService.java` | `36C95BC73F7981C213BFE59EE261218E3945B364A2B08D7BEED4726CADB0E268` |
| `apps/api/src/test/java/vn/covie/needs/api/NeedEligibleItemsControllerIntegrationTest.java` | `156E29EBCAD4B5792D78B20FCDF2CCFC6D2F009069CC487906D4E85DB4EEA252` |

Parent imports only the reviewed one-line service flush, six new tests/helpers/class-scoped scheduler isolation/test-only controllable clock, and this report. The isolated class already contains the parent's prior Need logging coverage; preserve it rather than overwriting the primary class with an older snapshot. Query-plan source/report are separately owned. No commit, branch, push, deployment or feature activation was performed here. Whole-project/production completion is not claimed.
