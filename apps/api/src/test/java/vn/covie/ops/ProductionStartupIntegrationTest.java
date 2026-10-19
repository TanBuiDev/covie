package vn.covie.ops;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.CovieApplication;

class ProductionStartupIntegrationTest {
    @TempDir Path directory;
    @Test void productionContextStartsWithConfiguredKeysAndDisposableDatabase() throws Exception {
        var key=KeyPairGenerator.getInstance("RSA"); key.initialize(2048); var pair=key.generateKeyPair();
        var privateFile=directory.resolve("private.pem"); var publicFile=directory.resolve("public.pem");
        Files.writeString(privateFile,pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
        Files.writeString(publicFile,pem("PUBLIC KEY",pair.getPublic().getEncoded()));
        try(var pg=new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("prod_startup_test").withUsername("test").withPassword("test-only");
                var redis=new org.testcontainers.containers.GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)) {
            pg.start();
            redis.start();
            try(var context=new SpringApplicationBuilder(CovieApplication.class).run(
                    "--spring.profiles.active=prod","--server.port=0","--management.server.port=0",
                    "--spring.datasource.url="+pg.getJdbcUrl(),"--spring.datasource.username="+pg.getUsername(),"--spring.datasource.password="+pg.getPassword(),
                    "--covie.production.public-url=https://covie.test","--covie.auth.issuer=https://covie.test",
                    "--covie.auth.jwt.private-key-path="+privateFile,"--covie.auth.jwt.public-key-path="+publicFile,
                    "--covie.auth.cookies.secure=true","--covie.rate-limit.hmac-key="+"T".repeat(40),
                    "--spring.data.redis.url=redis://"+redis.getHost()+":"+redis.getMappedPort(6379),
                    "--covie.rate-limit.policies.PUBLIC_DISCOVERY.capacity=1",
                    "--covie.rate-limit.policies.PUBLIC_DISCOVERY.refill-period=1h",
                    "--spring.security.oauth2.client.registration.google.client-id=test.apps.googleusercontent.com",
                    "--spring.security.oauth2.client.registration.google.client-secret=test-only-provider-secret",
                    "--spring.security.oauth2.client.registration.google.redirect-uri=https://covie.test/api/v1/auth/google/callback/google",
                    "--covie.storage.bucket=test-media","--covie.storage.region=us-east-1",
                    "--covie.storage.endpoint=","--covie.storage.public-base-url=https://media.covie.test",
                    "--covie.retention.enabled=false","--covie.outbox.max-events-per-poll=0",
                    "--covie.metrics.database-refresh-ms=100",
                    "--covie.bootstrap.reference-data.enabled=false","--covie.bootstrap.first-admin-email=")) {
                assertThat(context.isActive()).isTrue();
                assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
                assertThat(context.getBean(ProductionSafetyValidator.class)).isNotNull();
                assertThat(context.getBean(vn.covie.ratelimit.RateLimitProductionValidator.class)).isNotNull();
                assertThat(context.getBean(vn.covie.media.storage.ProductionStorageValidator.class)).isNotNull();
                assertThat(context.getBean(JdbcTemplate.class).queryForObject("select count(*) from flyway_schema_history where version='017' and success",Long.class)).isEqualTo(1);
                int apiPort=context.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
                int managementPort=context.getEnvironment().getRequiredProperty("local.management.port",Integer.class);
                assertThat(managementPort).isNotEqualTo(apiPort);
                var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
                for(String probe:java.util.List.of("readiness","liveness")) {
                    var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+managementPort+"/actuator/health/"+probe))
                            .timeout(java.time.Duration.ofSeconds(5)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    var health=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                    assertThat(health.path("status").asText()).isEqualTo("UP");
                    assertThat(health.has("components")).isFalse();
                }
                var denied=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+apiPort+"/api/v1/me"))
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(denied.statusCode()).isEqualTo(401);
                var discovery=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+apiPort+"/api/v1/categories"))
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build();
                assertThat(http.send(discovery,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                var throttled=http.send(discovery,java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(throttled.statusCode()).isEqualTo(429);
                assertThat(Long.parseLong(throttled.headers().firstValue("Retry-After").orElseThrow())).isPositive();
                assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(throttled.body()).path("error").path("code").asText())
                        .isEqualTo("RATE_LIMIT_EXCEEDED");
                context.getBean(ApiErrorMetrics.class).record(vn.covie.shared.web.ErrorCode.RESOURCE_VERSION_CONFLICT, 409);
                var eventId = java.util.UUID.randomUUID();
                var jdbc = context.getBean(JdbcTemplate.class);
                jdbc.update("insert into outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at,status,attempts) "
                        + "values (?,'METRIC_TEST','TEST',?,'{}'::jsonb,now(),'PENDING',0)", eventId, eventId);
                var dueGauge = context.getBean(io.micrometer.core.instrument.MeterRegistry.class).get("covie.outbox.due").gauge();
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (dueGauge.value() != 1 && System.nanoTime() < deadline) Thread.sleep(25);
                assertThat(dueGauge.value()).isEqualTo(1);
                assertThat(jdbc.queryForObject("select status from outbox_events where id=?", String.class, eventId)).isEqualTo("PENDING");
                var scrape=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://127.0.0.1:"+managementPort+"/actuator/prometheus"))
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(scrape.statusCode()).isEqualTo(200);
                String scrapeOutput = System.getProperty("covie.test.prometheus-scrape-output");
                if (scrapeOutput != null) Files.writeString(Path.of(scrapeOutput), scrape.body());
                assertThat(scrape.body()).contains("covie_outbox_backlog", "covie_retention_last_success_unixtime",
                        "covie_ratelimit_requests_total", "group=\"PUBLIC_DISCOVERY\"", "outcome=\"denied\"");
                assertThat(scrape.body()).contains("http_server_requests_seconds_count");
                assertThat(scrape.body()).contains("http_server_requests_seconds_bucket");
                assertThat(scrape.body()).contains("covie_outbox_oldest_due_seconds", "covie_outbox_terminal_failed");
                assertThat(scrape.body()).contains("covie_scheduler_runs_total", "covie_scheduler_failures_total",
                        "covie_scheduler_duration_seconds_count");
                assertThat(scrape.body()).contains("covie_sse_connections");
                assertThat(scrape.body()).contains("covie_moderation_open_cases", "covie_moderation_oldest_open_seconds",
                        "covie_media_ready_declared_bytes", "covie_catalog_video_delivery_failures_total");
                assertThat(scrape.body()).contains("covie_outbox_due", "covie_api_errors_total", "code=\"RESOURCE_VERSION_CONFLICT\"", "status=\"409\"");
                for (String job : java.util.List.of("outbox", "orders", "needs", "retention", "sse-heartbeat")) {
                    assertThat(scrape.body()).contains("job=\"" + job + "\"");
                }
            }
        }
    }
    private String pem(String type,byte[] bytes) {
        return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder().encodeToString(bytes)+"\n-----END "+type+"-----\n";
    }
}
