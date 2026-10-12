# Technical retention

The production scheduler removes only bounded technical state. It never deletes orders, order events, audit logs, users, merchant/catalog records, need requests/responses, or moderation evidence.

Each run takes PostgreSQL advisory lock `591000010` and deletes at most `covie.retention.batch-size` rows per technical dataset: expired idempotency records, expired checkout sessions, expired auth action tickets, refresh tokens with no incoming replacement references followed by empty expired sessions, and published outbox events older than the configured published-outbox retention. A token's `replaced_by_token_id` points to its newer replacement, so a chain drains from the oldest predecessor across sweeps; referenced replacement tokens are retained until their predecessors have been removed.

Production defaults are a seven-day grace after expiry and 30 days for published outbox events. The scheduler is idempotent and safe to rerun. Metrics expose deleted rows, run outcome and last-success time. Failed/PENDING outbox events are deliberately retained for investigation/replay.

The session advisory lock, cleanup statements and unlock use the same borrowed JDBC connection. A worker that cannot acquire the lock skips the sweep. Cleanup retains per-statement commits: if a later statement fails, earlier deletions may already be committed and the next sweep resumes the bounded cleanup. The connection is released after the unlock attempt.

After Plan 09 is merged, compare its schema with this allow-list. New technical tables must be explicitly classified before the production rehearsal; do not broaden the retention query to business/audit tables.
