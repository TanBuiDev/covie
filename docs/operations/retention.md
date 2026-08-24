# Technical retention

The production scheduler removes only bounded technical state. It never deletes orders, order events, audit logs, users, merchant/catalog records, need requests/responses, or moderation evidence.

Each run takes PostgreSQL advisory lock `591000010` and deletes at most `covie.retention.batch-size` rows per technical dataset: expired idempotency records, expired checkout sessions, expired auth action tickets, leaf refresh tokens followed by empty expired sessions, and published outbox events older than the configured published-outbox retention. Refresh-token chains naturally drain tail-first across sweeps so foreign-key lineage is never bypassed.

Production defaults are a seven-day grace after expiry and 30 days for published outbox events. The scheduler is idempotent and safe to rerun. Metrics expose deleted rows, run outcome and last-success time. Failed/PENDING outbox events are deliberately retained for investigation/replay.

After Plan 09 is merged, compare its schema with this allow-list. New technical tables must be explicitly classified before the production rehearsal; do not broaden the retention query to business/audit tables.
