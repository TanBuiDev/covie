# Demo data lifecycle

Reconciled on 2026-10-15. Synthetic demo data is separate from production data and
from the existing recovered local dataset. Archiving historical branches does not
authorize seeding, resetting or converting any existing database.

## Current tooling

`scripts/seed-ui-fixture.ps1` is the current local UI fixture entrypoint. It accepts
`PostgresContainer` (default `compose-postgres-1`) and verifies that the container is
running. Its fixed fixture identities, credentials and business rows are for local
testing. The running-container check does not establish that a target is disposable
or non-production; verify its identity and obtain authorization for its data changes
before using it. This branch cleanup did not run it.

The application reference bootstrap is distinct from scenario data. See
[Pilot bootstrap](pilot-bootstrap.md) for the current reference/admin procedures.
Do not treat enabling reference bootstrap as a request to import old demo orders,
users or merchant data.

## Historical tooling disposition

The retired `demo-data-v1` and integrated-UI branches contain the old seed/reset,
media import, generator, tests and `docs/DEMO_DATA.md`. They are preserved in
`.local/branch-retirement-20261015.bundle`; they were not copied into the current
application tree or executed. Their schema assumptions require a separate review
before any future reuse against V019 or later.

The local `.local/demo-seed-regeneration` worktree remains intact, including its
four tracked edits and ignored forensic files. Its local branch is retained as
active unpublished demo work; it is not evidence of production-ready demo tooling.
The edits also have verified file backups and a binary patch under
`.local/branch-retirement-20261015/worktree-2/`.

## Operating boundaries

- Identify the intended host/container, database and environment before any seed,
  reset or media import. Do not select a target merely by matching its name.
- Keep synthetic accounts/media outside production; do not commit passwords,
  tokens or private media credentials as evidence.
- For staging, follow [Staging rehearsal](staging-rehearsal.md) with its isolated
  resources and normal authenticated test identities. Local fixture tests do not
  substitute for staging acceptance.
- Use [Backup and restore](backup-restore.md) to restore into an explicitly selected
  empty target. Restore and reseed are different operations.
- Preserve the recovered dataset; no seed/reset/import was performed in the
  branch retirement operation.

See [Database environments](database-environments.md) and
[Branch retirement record](branch-retirement-2026-10-15.md) for configuration and
archive recovery details.
