# Social V2 — measured Seller moderation-reason lookup

## Scope

The newly added reason read had issued-JWT/privacy/schema/browser evidence but no representative case-history query plan. This checkpoint measures only that new conditional lookup, not the unchanged Admin history endpoint or full Seller detail/auth query graph. No production code, index, migration or API/client change is justified by the results.

New opt-in `SellerModerationReasonQueryPlanIntegrationTest` uses the existing Admin case-plan fixture scale: one merchant,20k catalog items,200050 cases (10/item plus50 extra for one item), adding200050 related decision/history actions so the new join is exercised. Most historical actions are CLOSE with CLOSED cases; current HIDE/REJECT actions are on RESOLVED cases. Target HIDDEN has60 cases/actions, REJECTED10, APPROVED10. Codes/notes in other records are private sentinels, not user/runtime data.

This is a synthetic management-read fixture, not proof of complete publishing/offer flows, realistic moderation distribution, a very heavy account or staging traffic.

## Capturing executed SQL

Actual InitialSessionIssuer OWNER JWT → MockMvc authenticated Seller detail route for each of the three item states. The real JdbcTemplate is spied, not its business results stubbed. Capture only the outer reason-query overload and its actual bound item UUID/action parameters; then run EXPLAIN ANALYZE BUFFERS FORMAT JSON on that exact executed SQL. No forced planner settings or guessed equivalent SQL.

The response must have the matching CURRENT_HIDE/CURRENT_REJECT code, omit the field for APPROVED, and omit Admin note/case-opening/historical-close sentinels. The test checks one conditional lookup for each restricted state and zero for APPROVED; these are **not total HTTP/auth/detail query counts**.

## Results

| State | Reason lookup count | Exact SQL execution time | Single HTTP sample | Response bytes |
| --- | --- | --- | --- | --- |
| HIDDEN /60 cases | 1 | 0.209ms | 223.3495ms | 417 |
| REJECTED /10 cases | 1 | 0.088ms | 26.8498ms | 421 |
| APPROVED | 0 | Not issued | 23.0428ms | 381 |

Natural plans use existing `ix_moderation_cases_catalog_recent` (V021) and `ix_moderation_actions_case` (V013). No sequential scan of either large moderation table. The HIDDEN plan reads60 scoped case rows via12 heap blocks and performs60 indexed action probes, then sorts the matching single result in memory (25KiB). This database nested-loop execution is not60 application SQL requests. No temp IO or shared disk reads in the measured warm plans; no cold-cache claim.

The conditional field has one extra JDBC lookup per eligible detail request, not an additional lookup per returned media/variant/list row. No new index, denormalization, query refactor or cache is needed on this evidence.

## Reproduction and validation

Sequential isolated API copy, disposable PostgreSQL, transaction rollback:

```text
mvnw.cmd -o -Dtest=SellerModerationReasonQueryPlanIntegrationTest -Dcovie.test.seller-reason-plan=true -Dcovie.test.seller-reason-plan-output=E:/WebApp/CoVie/.local/social-v2-g2-seller-reason-plans test
```

- `.local/social-v2-g2-seller-reason-plans.log`: **1 PASS**, test27.22s, Maven36.982s, no failures/errors/skips.
- Exact executed SQL, unforced JSON plans and measurements at `.local/social-v2-g2-seller-reason-plans/`.
- Fixture case/action counts remain200050; a three-row catalog sample remains unchanged. This is not a new full-table immutability claim; the earlier dedicated readonly/privacy integration test already covers the unchanged production read code.
- Source/private-copy management service hashes match. Targeted diff check passes. No full BE/web/client/schema/browser rerun, recovered/runtime DB access, normal restart, activation, Git commit/branch/remote action or deployment.

## Gate limits and next action

Local analyzed/warm fixtures and three single HTTP observations are not P95, load/concurrency, representative high decision density or staging SLO evidence. G6 performance/release and storage/CDN/privacy infrastructure gates stay UNVERIFIED. Whole V2 ACTIVE; normal V2/video flags OFF.

Next: trace the application's actual logout/login/account-switch lifecycle and client state reset for G3/G4. Verify whether those real flows already remount/reconcile state before adding events, polling or tests for artificial eventless cookie replacements. Keep the prior manual-cookie/synthetic-focus proof scoped as documented.
