# Original Plan06 Need query-path evidence — 2026-10-20

Scope: only original `docs/CoVie-technical-plan-06-need-requests-matching-v1.0.md:3370` Task20Step7. This is targeted local SQL evidence, not whole Plan06/project acceptance, deployment, provider traffic, real-user load or optimizer tuning.

## Actual verification

- New test-only `apps/api/src/test/java/vn/covie/needs/persistence/NeedOriginalQueryPlanIntegrationTest.java` runs in the isolated checkout `C:/Users/tanbu/.codex/worktrees/handoff-integration/CoVie`. Source SHA256 `B2406132BAC7FED5B9D5308B9E446B0D68F47F6976DA5BD36B49E814F090525C`; original pilot SQL SHA256 `A0C4DAE256FA5D5CBCD254D5309CCC71FE53F52AD9DA7A485C4A04925D84C843`.
- Exclusive Maven runner executed only this class offline: **1PASS/0failure/0error/0skip**, test44.92s/Maven49.519s, finished2026-10-20T21:24:12+07. Actual log: isolated `.local/need-original-query-plans-20261020.log`. Compilation/run was performed by the coordinated Plan06–10 runner; this report reads its completed output, not a fabricated independent rerun.
- Fresh owned PostgreSQL18/PostGIS3.6 container, Spring test profile, explicit outbox polling0 and retentionOFF, enclosing rollback transaction; no normal/recovered database, normal app flags, provider, credentials, invoice/payment or Git operation. The container is stopped by the test's AfterAll hook; existing normal Compose containers were not stopped/altered.
- Actual existing full pilot fixture:1000users/1000merchants/2000locations/20000catalogitems/30000variants/40000offers/10000Needs, mixed states and spatial points, plus historical Orders/events/notification/outbox/cart data. Added10000response rows (8000ACTIVE/2000WITHDRAWN) and10000response-item rows (8000ACTIVE/2000RETIRED); first1000responses share Need501 across1000distinct locations, remaining rows distribute over other Needs. Selected page returns21/800ACTIVE responses for that Need, so response-list access is not empty/one-row fake evidence.
- Existing pilot Needs have7000ACTIVE;20are backdated to due expiry, leaving6980futureACTIVE at capture time. Expiry repository returns/locks10 due rows. Test fixture injection is not scheduler lifecycle acceptance.
- Default planner confirmed `enable_seqscan=on`; `ANALYZE` runs after fixture population. No index hint, forced sequential-scan disable, speculative migration/index change or application SQL rewrite.

## Exact eight original paths

Artifacts are under isolated `.local/need-original-plans-20261020/`: every label has actual `.sql`, `-bindings.txt` and `-plan.json` (**27files**,9captures). SQL is captured from real Hibernate repository invocation or real `JdbcNeedDiscoveryRepository` NamedParameterJdbcTemplate call; EXPLAIN reuses the same SQL and typed/bound inputs. Each JSON root has positive actual rows, execution time and BUFFERS metadata.

The eight original labels are not eight independent endpoint implementations: geo, category/type and text/trigram ranking are clauses of the actual composite discovery repository query. Captures03/04/05/06 execute that full unchanged query with the stated bindings;04/06 use identical single-location inputs and are intentionally disclosed as the same path measured for two requirement clauses. They must NOT be counted as four distinct query families or synthesized fragment-SQL proof.09is the buyer response-item batch query required to inspect the final listed index, not a ninth original requirement.

| Capture / original named path | Actual invocation and semantic assertion | Rows | EXPLAIN execution ms | Shared hits / reads / temp writes |
|---|---|---|---|---|
| 01-buyer-need-list: buyer Need list | NeedRequestRepository.findByBuyerUserIdOrderByCreatedAtDescIdDesc; buyer1 has exactly10owned Needs | 10 | 0.142 | 12/0/0 |
| 02-active-expiry: active Need expiry scan | NeedRequestRepository.claimExpiredBatch(now,10);10due rows, actual FOR UPDATE SKIP LOCKED | 10 | 0.102 | 22/0/0 |
| 03-seller-merchant-location-discovery: seller merchant/location Need discovery | JdbcNeedDiscoveryRepository.findCandidates; merchant1's two allowed locations,31nonempty rows constrained to those locations | 31 | 64.58 | 15817/0/0 |
| 04-need-geo-radius: Need geo radius filter | Full production discovery single location;31results each distance<=5000m and exact requested location; actual st_dwithin predicate retained | 31 | 53.743 | 11168/0/0 |
| 05-need-category-type: Need category/type filter | findCandidateForNeed Need501;1exact Need row, PRODUCT/category1; real ci.item_type=n.need_type and recursive category join retained | 1 | 0.628 | 286/0/0 |
| 06-catalog-text-trigram-against-need: Catalog full-text/trigram relevance against Need query text | Full discovery with Needquery rice;31positive text ranks, actual ts_rank/websearch_to_tsquery/similarity against n.query_text retained | 31 | 46.245 | 11168/0/0 |
| 07-active-response-lookup: active response lookup | NeedResponseRepository.findByNeedRequestIdAndMerchantLocationIdAndResponseStatus; exact Need501/location1 ACTIVE row present | 1 | 0.026 | 3/0/0 |
| 08-buyer-response-list: buyer response list | NeedResponseRepository.findPage ACTIVE with page21; nonempty bounded page over800eligible rows | 21 | 0.052 | 3/0/0 |
| 09-buyer-response-items: extra response-item batch lookup | NeedResponseItemRepository.findByNeedResponseIdInAndItemStatusOrderByCreatedAtAsc; one selected response/one ACTIVEitem | 1 | 0.024 | 3/0/0 |

These are warm local EXPLAIN timings, not HTTP p95, concurrent traffic, production SLO, WAN, soak or upper latency bounds. No temp blocks were read/written in any of the9captures; shared reads0reflects the warmed fixture, not a claim of no physical I/O under deployment.

## Actual index disposition — no forced-green claim

| Original listed index | Observed access / limitation |
|---|---|
| ix_need_requests_buyer_created | Capture01 Bitmap Index/Heap path,10rows; no full Need scan |
| ix_need_requests_status_expiry | Capture02 Index Scan +LockRows +Limit,10rows |
| ix_need_requests_category_type_state | **Not selected** by default planner in these actual captures. Category/type predicate runs in actual composite query; selected-Need05uses need_requests_pkey. Index existence/forced-plan test is not claimed as usage |
| ix_need_requests_geo | **Not selected** by default planner. Variable per-Need search radius is evaluated by actual st_dwithin after the futureACTIVE cohort index path. No fabricated GiST-use claim |
| ux_need_response_active_location | Capture07 exact lookup and composite discovery already_responded subplans use this index |
| ix_need_responses_need_state | Capture08 Index Scan +Incremental Sort +Limit,21root rows;22index rows visited |
| ix_need_response_items_response_state | Capture09 Index Scan,1row |

Composite captures03/04/06 use newer `ix_need_active_moderation_expiry` for7000candidate index entries/6980futureACTIVE heap rows, then authorized merchant/location/offer indexes (`ix_merchant_locations_profile_status` or merchant_locations_pkey; `ix_location_variant_location_available`) and variants/catalog primary keys.03matches381Need/location groups from7270joinedrows and returns31;04/06match173groups from5190joinedrows and return31. Full-text/trigram rank is evaluated after the merchant offer/category candidate chain; no catalog FTS/trigram GIN index use is claimed.05uses the requested Need primary key and returns1group.

Sequential scans observed are the small10-row category hierarchy, empty inventory_balances (fixture UNTRACKED), and05's small location_areas lookup (100table rows;11visited per30loops). No full need_requests/need_responses/need_response_items Seq Scan appears in these captures. Small-table Seq Scans are reported, not automatically treated as defects or suppressed.

## Bounded conclusion

- Original eight named SQL paths now have actual populated default-planner EXPLAIN ANALYZE BUFFERS captures and nonempty semantic assertions. This replaces the previous unsupported assumption that old `NeedQueryPlanIntegrationTest` index-existence/enable_seqscanOFF checks were complete query evidence.
- **Five of seven originally listed indexes are observed in this run; two are not selected.** Therefore literal “every listed index used” is NOT marked PASS. Actual query review is complete for this scoped fixture, while that index-usage expectation has a documented planner deviation. No measured bad path/SLO failure justifies tuning from this single synthetic dataset, and the plan expressly forbids forcing index hints.
- No production performance defect, production optimization, new index/migration, entire matching/ranking correctness proof, selected-category negative matrix, source checkout lifecycle, multi-replica scheduler or full Plan06 completion is inferred. Other current Need command tests/source changes have separate receipts; results are not merged into one new full-suite claim.
- The new test is local source until parent integration/publication. Original normal/recovered data and BillingON controls remain unchanged.
