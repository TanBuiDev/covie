# Pilot bootstrap

The pilot bootstrap is **disabled by default**. It has no HTTP endpoint and must never create customers, merchants, Orders, passwords, OAuth identities, inventory, reviews or moderation cases.

## Prerequisite

Bootstrap refuses to run unless Flyway has reached **V013 or later**. This protects the operator from running Plan 10 bootstrap code against an incomplete Plans 01–09 schema.

## Reference data

Set only for the one restart used to reconcile the committed Grand Park reference set:

```text
COVIE_BOOTSTRAP_REFERENCE_DATA_ENABLED=true
COVIE_BOOTSTRAP_REFERENCE_DATA_PATH=classpath:/bootstrap/grand-park-reference-data.json
```

The JSON contains stable human slugs. The runner processes parents first, preserves existing UUIDs, updates only reference attributes and never deletes rows omitted from the seed. Rerunning is intended to be idempotent. After a successful run, disable the flag and restart normally.

Before production use, run the bootstrap integration tests against the exact database schema that includes V013 and verify the expected `location_areas`/`categories` columns. If the merged Plan 09 schema differs from the Plan 10 contract, align the bootstrap SQL before enabling it; do not weaken the V013 guard.

## First admin

1. The intended admin completes the normal Google registration flow and mandatory CoVie email/password onboarding.
2. Confirm the user is ACTIVE and the Google identity is verified.
3. Set `COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL` for one restart.
4. The runner requires exactly one matching active user with both verified Google and local EMAIL identity.
5. It grants only the existing `ADMIN` system role with `ON CONFLICT DO NOTHING` and writes a `FIRST_ADMIN_GRANTED` security audit row when the grant is new.
6. Remove `COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL` and restart again.

Zero or multiple matching users is a startup failure. The runner never creates a user, password or OAuth identity and does not expose a backdoor endpoint.
