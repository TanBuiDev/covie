# Local development lifecycle

## GPS address suggestions and profile editing

The address form now uses one **Địa chỉ giao hàng** section containing editable text, GPS/autofill and the interactive delivery map. Click a place on the map or pan with mouse/keyboard and use **Chọn điểm ở giữa bản đồ**. Changing the text or point invalidates the previous delivery confirmation. Source selection and numerical coordinates are under **Nâng cao**; choosing manual coordinates starts from the selected point. GPS only fills the suggestion on explicit request, and map movement does not trigger reverse lookups.

The Leaflet map uses the standard OpenStreetMap tile endpoint and visible attribution, with no offline prefetch or cache bypass. It requires internet access; GPS/manual entry remains available if tiles fail. Follow the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) when selecting tile infrastructure for a public deployment.

Profile editing supports display name, an uploaded avatar and private phone. Restart the API after updating code so Flyway applies `V016__profile_phone.sql`; this adds a nullable column without replacing user records. Google login email remains the account identity.

For the explicitly selected local OpenStreetMap/Nominatim option, set `COVIE_GEOCODING_REVERSE_URL=https://nominatim.openstreetmap.org/reverse` in ignored `.env.local`. The default is disabled. The configurable identifying `COVIE_GEOCODING_USER_AGENT` is supplied by the API. Restart with Ctrl+C followed by `pnpm dev` to load configuration and code.

Clicking the GPS button sends only coordinates to the provider through the authenticated API. Suggestions remain editable and may lack house/building/apartment numbers. The written address describes the recipient's location; coordinates select that same place on the map for distance calculations. Confirm the delivery point after checking both. If lookup fails or is rate limited, retain the coordinates and enter the address manually.

The local service caches results for one hour (maximum 256 entries) and limits requests to one per second per API instance. Attribution is shown with the suggestion. This configuration is intended for one local API instance; a public deployment must provide an application-wide rate limit and select a provider suited to its traffic. See the [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) and [reverse lookup documentation](https://nominatim.org/release-docs/latest/api/Reverse/). No autocomplete requests are sent.

Run `pnpm dev` from the repository root. The launcher imports the ignored root `.env.local` (including Google OAuth and `DB_URL`), starts the existing Compose configuration with runtime/restore overrides, starts API on 8081 and web on 3000, and stays in the foreground.

Press **Ctrl+C** to stop the managed API/web process trees and run the same Compose configuration's `stop`. A lightweight hidden watchdog checks the foreground owner's identity every two seconds; if the owner disappears after its terminal is closed, the watchdog invokes the same cleanup and then exits. `pnpm dev:stop` can also stop the session from another terminal. Closing Windows abruptly or losing power cannot guarantee immediate cleanup; Docker Desktop's own shutdown handles its containers in that case.

Cleanup retains containers, PostgreSQL/MinIO/Redis volumes and data. Docker Desktop itself is not stopped, and other projects' services are not included in this Compose invocation. Docker must already be available; the launcher does not install/start the Docker Desktop application.

SSR refresh now requires the root `.env.local` `REDIS_URL` for shared coordination. Compose Redis uses the named `covie_redis` volume, AOF with `appendfsync always` and `noeviction`; the normal `pnpm dev` Compose startup applies this configuration when recreating the service. Preserve that volume during ordinary maintenance. Web checks these settings before rotating tokens and fails with503 if Redis is unavailable/unsafe; no RAM fallback. Details and recovery limits: [SSR refresh coordination](ssr-refresh-coordination.md).

The first transition from the old detached launcher requires stopping the old API terminal with Ctrl+C and running `pnpm dev:stop` to stop the old recorded web/Compose services. Then run `pnpm dev`. Occupied API/web ports are rejected before startup rather than adopting an independently started process. Subsequent starts need only `pnpm dev`.

Logs and session identity are ignored files under `.local/dev/`. PID plus process creation time and expected repository/command protect cleanup from PID reuse. Session IDs prevent an old watchdog from shutting down a later session. Cleanup is serialized across Ctrl+C/watchdog/manual stop. Failed Docker shutdown retains the session for a `pnpm dev:stop` retry.

Verification: `scripts/tests/dev-script.test.ps1` validates syntax/launcher contract and Java discovery; `scripts/tests/dev-lifecycle.test.ps1` executes cleanup with isolated process/Docker doubles and verifies session isolation, PID reuse, volume-preserving Compose stop, idempotence, failed shutdown retry and owner-disappearance watchdog behavior. These checks do not interrupt the user's currently running services. Full live start/Ctrl+C/terminal-close acceptance is not claimed by these tests.
