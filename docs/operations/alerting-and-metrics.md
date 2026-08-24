# Metrics and alerting

Spring Boot management runs on private port 9091 in production. Prometheus scrapes `/actuator/prometheus` on the Docker network; Nginx never exposes management endpoints. Grafana is bound to loopback port 3001 so access requires host/VPN/SSH tunneling.

Pilot signals include HTTP traffic/error rates, JVM/process metrics, Redis rate-limit denied/degraded outcomes, technical-retention success/deletes, outbox backlog, expired checkout sessions, host filesystem capacity and PostgreSQL backup age. Alertmanager reads its webhook URL from a mounted secret file.

Before pilot launch, deploy the observability overlay, confirm all targets are UP, trigger a disposable test alert, verify delivery and resolution, and record screenshots/query output in the staging evidence directory. Thresholds are pilot defaults and should be tightened only after measured baseline traffic exists.
