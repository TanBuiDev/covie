# CoVie production topology

CoVie V1 pilot uses one public ingress: Nginx on ports 80/443. Nginx terminates TLS, redirects HTTP to HTTPS, proxies `/api/v1/*` to the Spring Boot API on the private Docker network, and proxies all other routes to the Next.js Web container. API port 8080, management port 9091, Web port 3000, PostgreSQL, Redis, Prometheus, Grafana and Alertmanager are not internet-published.

The API and Web containers are stateless and run as numeric non-root UID 10001. PostgreSQL/PostGIS remains the transactional source of truth. Redis is only an abuse-control/cache dependency. Object storage contains media bytes; PostgreSQL remains authoritative for media metadata and readiness.

`/actuator/*` is intentionally absent from the public Nginx routing table. Prometheus reaches `api:9091/actuator/prometheus` only through `covie_internal`. SSE at `/api/v1/me/events` has Nginx buffering/cache disabled and a long read timeout; reconnect semantics remain REST refetch, not durable replay.

Production release uses immutable API/Web image digests and a controlled Compose restart. Database rollback is forward-fix by default; application rollback switches image digests and never performs an automatic Flyway down migration.
