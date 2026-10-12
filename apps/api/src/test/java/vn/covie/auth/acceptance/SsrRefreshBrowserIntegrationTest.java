package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Opt-in: built Next/Node/Chrome with disposable PostgreSQL and durable Redis only. */
@EnabledIfSystemProperty(named = "covie.test.ssr-browser", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "covie.auth.cookies.secure=false", "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"
})
class SsrRefreshBrowserIntegrationTest extends AuthAcceptanceSupport {
    @LocalServerPort int apiPort;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @org.springframework.beans.factory.annotation.Autowired vn.covie.auth.security.AuthProperties authProperties;

    private org.testcontainers.containers.GenericContainer<?> redisFixture() {
        var redis = new org.testcontainers.containers.GenericContainer<>(
                org.testcontainers.utility.DockerImageName.parse("redis:8-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "always", "--save", "", "--maxmemory-policy", "noeviction");
        redis.start();
        return redis;
    }

    private int unusedPort() throws Exception {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private Process startWeb(int port, String publicUrl, String redisUrl, String log) throws Exception {
        Path web = Path.of("../web").toAbsolutePath().normalize();
        var launcher = new ProcessBuilder(System.getProperty("covie.test.node", "node"),
                web.resolve("node_modules/next/dist/bin/next").toString(), "start", "-p", Integer.toString(port), "-H", "127.0.0.1")
                .directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL", "http://127.0.0.1:" + apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL", publicUrl);
        launcher.environment().put("REDIS_URL", redisUrl);
        launcher.redirectErrorStream(true).redirectOutput(Path.of("../../.local/" + log).toFile());
        return launcher.start();
    }

    private void awaitWeb(Process process, String url) throws Exception {
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
        for (int attempt = 0; attempt < 60 && process.isAlive(); attempt++) {
            try {
                if (http.send(HttpRequest.newBuilder(URI.create(url + "/login")).timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
            } catch (Exception ignored) { }
            Thread.sleep(500);
        }
        throw new AssertionError("Built Next server did not start; inspect redacted runtime log");
    }

    @Test
    void twoNextProcessesAndLateRequestRotateExactlyOnceAgainstRealBackend() throws Exception {
        var user = activeUser("Distributed SSR buyer");
        var session = issuer.issue(user.getId(), device());
        int firstPort = unusedPort(), secondPort = unusedPort();
        String firstUrl = "http://127.0.0.1:" + firstPort, secondUrl = "http://127.0.0.1:" + secondPort;
        try (var redis = redisFixture()) {
            String redisUrl = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            Process first = startWeb(firstPort, firstUrl, redisUrl, "ssr-redis-next-first-20261010.log");
            Process second = null;
            try {
                second = startWeb(secondPort, firstUrl, redisUrl, "ssr-redis-next-second-20261010.log");
                awaitWeb(first, firstUrl); awaitWeb(second, secondUrl);
                var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
                var request1 = HttpRequest.newBuilder(URI.create(firstUrl + "/api/v1/auth/ssr-refresh?returnTo=%2Fcart"))
                        .header("Cookie", "covie_rt=" + session.rawRefreshToken() + "; covie_refresh_binding=" + "A".repeat(43)).header("Sec-Fetch-Site", "same-origin")
                        .timeout(Duration.ofSeconds(45)).GET().build();
                var request2 = HttpRequest.newBuilder(URI.create(secondUrl + "/api/v1/auth/ssr-refresh?returnTo=%2Fme"))
                        .header("Cookie", "covie_rt=" + session.rawRefreshToken() + "; covie_refresh_binding=" + "A".repeat(43)).header("Sec-Fetch-Site", "same-origin")
                        .timeout(Duration.ofSeconds(45)).GET().build();
                var pending1 = http.sendAsync(request1, HttpResponse.BodyHandlers.discarding());
                var pending2 = http.sendAsync(request2, HttpResponse.BodyHandlers.discarding());
                var response1 = pending1.get(45, TimeUnit.SECONDS);
                var response2 = pending2.get(45, TimeUnit.SECONDS);
                assertThat(response1.statusCode()).isEqualTo(303);
                assertThat(response2.statusCode()).isEqualTo(303);
                assertThat(response1.headers().firstValue("Location")).contains(firstUrl + "/cart");
                assertThat(response2.headers().firstValue("Location")).contains(firstUrl + "/me");
                var cookies1 = response1.headers().allValues("Set-Cookie").stream().map(value -> value.split(";")[0])
                        .filter(value -> value.startsWith("covie_at=") || value.startsWith("covie_rt="))
                        .map(fingerprints::sha256Hex).sorted().toList();
                var cookies2 = response2.headers().allValues("Set-Cookie").stream().map(value -> value.split(";")[0])
                        .filter(value -> value.startsWith("covie_at=") || value.startsWith("covie_rt="))
                        .map(fingerprints::sha256Hex).sorted().toList();
                assertThat(cookies1).hasSize(2).isEqualTo(cookies2);
                assertThat(http.send(request2, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(303);
                assertThat(refreshTokens.findAllBySessionId(session.sessionId())).hasSize(2);
                assertThat(sessions.findById(session.sessionId()).orElseThrow().getRevokedAt()).isNull();
                sessionManagement.revokeForLogout(session.sessionId());
                var revoked = http.send(request2, HttpResponse.BodyHandlers.discarding());
                assertThat(revoked.statusCode()).isEqualTo(303);
                assertThat(revoked.headers().firstValue("Location")).contains(firstUrl + "/login?returnTo=%2Fme");
                assertThat(revoked.headers().allValues("Set-Cookie").stream().allMatch(value -> value.contains("Max-Age=0"))).isTrue();
            } finally {
                if (first.isAlive()) first.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                if (second != null && second.isAlive()) second.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class RequestTrace {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> ssrTestTrace() {
            return new org.springframework.boot.web.servlet.FilterRegistrationBean<>((request, response, chain) -> {
                chain.doFilter(request, response);
                System.out.println("SSR fixture API " + ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI()
                        + " " + ((jakarta.servlet.http.HttpServletResponse) response).getStatus());
            });
        }
    }

    @Test
    void realBrowserF5RefreshesExpiredAccessCookiesAndRejectsRevokedOrExpiredRefreshTokens() throws Exception {
        var user = activeUser("Isolated SSR buyer");
        var valid = issuer.issue(user.getId(), device());
        var expiredAccess = new vn.covie.auth.security.JwtTokenService(jwtEncoder, authProperties,
                () -> java.time.Instant.now().minus(Duration.ofMinutes(16)), new vn.covie.shared.id.UuidV7Generator())
                .issueAccessToken(user.getId(), valid.sessionId(), java.util.Set.of(vn.covie.shared.security.SystemRole.USER));
        var revoked = issuer.issue(user.getId(), device());
        sessionManagement.revokeForLogout(revoked.sessionId());
        var expired = issuer.issue(user.getId(), device());
        jdbc.update("update auth_refresh_tokens set expires_at=now()-interval '1 minute' where session_id=?", expired.sessionId());
        int webPort = unusedPort();
        String webUrl = "http://127.0.0.1:" + webPort, apiUrl = "http://127.0.0.1:" + apiPort;
        Path web = Path.of("../web").toAbsolutePath().normalize();
        String node = System.getProperty("covie.test.node", "node");
        try (var redis = redisFixture()) {
            Process next = startWeb(webPort, webUrl, "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379), "ssr-next-runtime-20261010.log");
            try {
                awaitWeb(next, webUrl);
                var runner = new ProcessBuilder(node, "e2e/ssr-refresh-browser.mjs").directory(web.toFile());
                runner.environment().put("COVIE_SSR_BROWSER_TEST", "1");
                runner.environment().put("COVIE_SSR_WEB_URL", webUrl);
                runner.environment().put("COVIE_SSR_API_URL", apiUrl);
                runner.redirectErrorStream(true).redirectOutput(Path.of("../../.local/ssr-browser-runtime-20261010.log").toFile());
                Process browser = runner.start();
                try {
                    try (var stdin = browser.getOutputStream()) {
                        stdin.write(new ObjectMapper().writeValueAsBytes(Map.of("access", valid.accessToken().value(),
                                "expiredAccess", expiredAccess.value(), "refresh", valid.rawRefreshToken(),
                                "revoked", revoked.rawRefreshToken(), "expired", expired.rawRefreshToken())));
                    }
                    boolean finished = browser.waitFor(90, TimeUnit.SECONDS);
                    assertThat(finished).as("browser scenario terminates").isTrue();
                    assertThat(browser.exitValue()).as("browser SSR scenario; inspect redacted runtime log").isZero();
                    assertThat(refreshTokens.findAllBySessionId(valid.sessionId())).hasSize(3);
                    assertThat(sessions.findById(valid.sessionId()).orElseThrow().getRevokedAt()).isNull();
                } finally { if (browser.isAlive()) browser.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
            } finally { if (next.isAlive()) next.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
