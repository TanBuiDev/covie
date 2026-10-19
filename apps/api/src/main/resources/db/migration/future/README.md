# Shared migrations

Place all future versioned migrations (V041 and later) here. Canonical Flyway scans
the parent db/migration recursively. The preserved Social lineage scans this shared
directory together with db/migration-social. Do not duplicate or renumber applied SQL.
