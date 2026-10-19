# Owned UI orchestration acceptance

Goal: execute the original G5 `pnpm ui:verify-local` safely without seeding the
existing/recovered default PostgreSQL container. No application behavior change.

1. Existing seed script already accepts `-PostgresContainer`; verify wrapper cannot
   forward it using a focused mocked orchestration regression (no Docker/browser).
2. Add one optional wrapper parameter and forward the same argument to both existing
   seed invocations, preserving defaults, spec ordering and single-worker behavior.
3. Run existing script regression plus argument/default propagation assertions.
4. Provision an explicitly owned disposable API/PG/Redis/Web fixture using current
   artifacts and generated private keys. Never rename or alter an existing container,
   overwrite recovered DB, run a duplicate normal stack or use original E `.next`.
5. Execute the exact pnpm command with explicit owned-container argument, collect
   actual Playwright results/screenshots, fix only evidenced in-scope regressions.
   Stop fixture resources after completion; do not relabel equivalent tests as this
   exact command or claim UI acceptance before it actually runs.

Backend full verify98183 is already running independently. Do not overlap Maven,
change Java source, or start a competing Web build/native fixture during that gate.

Next action: Run focused mocked wrapper argument regression before implementation.
Required from you: Nothing
Completion condition: Both seed calls safely receive the explicit owned DB while default orchestration remains unchanged.
