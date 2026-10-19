# G1: remaining list/detail query evidence inventory

Source audit only: `AdminCatalogReadService` and the existing case-history query
plan fixture were compared with G1.1's bounded-read/no-N+1 requirement. No new
performance defect is claimed and no production code or index is changed here.

## Not covered by the achieved history-index gate

- List performs one filtered total count, one limited/offset summary query with
  a correlated READY image cover selector, then one bounded media asset fetch
  when covers exist. The cover selector is part of the SQL statement, not proof
  of application-level N+1. Its actual inner-loop work needs measurement.
- Default updated ordering and the two allowed created orderings, merchant/type/
  listing/moderation filters and nonblank search were not exercised by the
  item-scoped moderation-case history plan fixture.
- Search intentionally includes escaped substring matching over item/merchant
  name and stringified IDs. A count or substring scan is not, by itself, evidence
  authorizing a contract change, different search semantics or speculative index.
- Detail performs summary/cover selection, a separate content read, ordered media
  metadata, READY asset batch, distinct location query and the already-tested
  case count/page pair. Actual full-request query counts and non-history query
  plans with representative READY/media/location data remain unmeasured.

## Next bounded measurement

Capture actually executed named SQL plus Hibernate asset selects on real issued
Admin JWT requests for default list at size 20 and maximum permitted size, one
combined permitted filter/search request and one detail with multiple media and
locations. Use a disposable, analyzed fixture; compare statement counts between
list sizes and inspect unforced EXPLAIN ANALYZE BUFFERS for only the new query
paths. Reuse the achieved history-index evidence without rerunning its separate
fixture or adding indexes in advance. Capture exact row limits, response shape,
fixture size and warm/local timing limitations. Any change requires an observed
failure or concrete measured risk, not the fact a sequential scan exists.

Other Admin browser, object privacy/concurrency, staging/load gates are separate
and remain open. Normal V2/video flags stay OFF; the whole objective is active.

Next action: Measure actual Admin list/detail SQL and query counts on the disposable fixture.
Required from you: Nothing
Completion condition: Bounded request observations and unforced plans establish whether a minimal fix is actually necessary.
