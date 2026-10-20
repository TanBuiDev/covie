package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.needs.model.NeedResponseStatus;

@SpringBootTest(properties = {"covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.needs.persistence.NeedOriginalQueryPlanIntegrationTest$SqlCapture"})
@ActiveProfiles("test")
@Transactional
class NeedOriginalQueryPlanIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("need_original_plans").withUsername("need_plans").withPassword("test-only");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll static void cleanup() { POSTGRES.stop(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired NeedRequestRepository needs;
    @Autowired NeedResponseRepository responses;
    @Autowired NeedResponseItemRepository items;

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<String> SQL = new ThreadLocal<>();
        @Override public String inspect(String sql) {
            if (sql.toLowerCase(java.util.Locale.ROOT).startsWith("select ")) SQL.set(sql);
            return sql;
        }
    }

    @Test
    void explainsTheEightOriginalNeedPathsWithActualRepositorySqlAndDefaultPlanner() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/performance/pilot-fixture.sql")) {
            assertThat(fixture).isNotNull();
            jdbc.execute(new String(fixture.readAllBytes(), StandardCharsets.UTF_8));
        }
        jdbc.execute("""
                insert into need_responses(id,need_request_id,merchant_profile_id,merchant_location_id,
                    response_status,created_by_user_id,created_at,updated_at,withdrawn_at,version)
                select md5('need-plan-response-'||series)::uuid,
                    md5('pilot-need-'||(case when series<=1000 then 501 else series end))::uuid,
                    md5('pilot-merchant-'||((series-1)%1000+1))::uuid,
                    md5('pilot-location-'||((series-1)%1000+1))::uuid,
                    case when series%5=0 then 'WITHDRAWN' else 'ACTIVE' end,md5('pilot-user-1')::uuid,
                    now()-series*interval '1 second',now(),
                    case when series%5=0 then now() else null end,0
                from generate_series(1,10000) series
                """);
        jdbc.execute("""
                insert into need_response_items(id,need_response_id,catalog_item_id,variant_id,
                    location_variant_id,item_status,price_snapshot,currency_code,availability_snapshot,created_at,retired_at)
                select md5('need-plan-item-'||series)::uuid,md5('need-plan-response-'||series)::uuid,
                    md5('pilot-item-'||((series-1)%1000+1))::uuid,
                    md5('pilot-variant-'||((series-1)%1000+1))::uuid,
                    md5('pilot-offer-'||((series-1)%1000+1))::uuid,
                    case when series%5=0 then 'RETIRED' else 'ACTIVE' end,10000,'VND','{}'::jsonb,now(),
                    case when series%5=0 then now() else null end
                from generate_series(1,10000) series
                """);
        jdbc.update("update need_requests set expires_at=now()-interval '1 minute' where id in "
                + "(select md5('pilot-need-'||series)::uuid from generate_series(401,420) series)");
        jdbc.execute("analyze");
        assertThat(jdbc.queryForObject("show enable_seqscan", String.class)).isEqualTo("on");
        assertThat(jdbc.queryForObject("select count(*) from need_requests", Long.class)).isEqualTo(10000);
        assertThat(jdbc.queryForObject("select count(*) from need_responses", Long.class)).isEqualTo(10000);
        assertThat(jdbc.queryForObject("select count(*) from need_response_items", Long.class)).isEqualTo(10000);
        Instant now = Instant.now();
        UUID buyer = id("pilot-user-1"), merchant = id("pilot-merchant-1");
        UUID location = id("pilot-location-1"), otherLocation = id("pilot-location-1001");
        UUID need = id("pilot-need-501");

        try {
            SqlCapture.SQL.remove();
            assertThat(needs.findByBuyerUserIdOrderByCreatedAtDescIdDesc(buyer)).hasSize(10);
            explain("01-buyer-need-list", SqlCapture.SQL.get(), buyer);

            SqlCapture.SQL.remove();
            assertThat(needs.claimExpiredBatch(now, 10)).hasSize(10);
            explain("02-active-expiry", SqlCapture.SQL.get(), Timestamp.from(now), 10);

            NamedCapture capture = new NamedCapture(jdbc.getDataSource());
            var discovery = new JdbcNeedDiscoveryRepository(capture);
            var candidates = discovery.findCandidates(merchant, Set.of(location, otherLocation), null, now, null, 31);
            assertThat(candidates).hasSize(31).allSatisfy(row ->
                    assertThat(row.merchantLocationId()).isIn(location, otherLocation));
            explainNamed("03-seller-merchant-location-discovery", capture);

            var radiusCandidates = discovery.findCandidates(merchant, Set.of(location), location, now, null, 31);
            assertThat(radiusCandidates).hasSize(31).allSatisfy(row -> {
                assertThat(row.distanceMeters()).isLessThanOrEqualTo(5000);
                assertThat(row.merchantLocationId()).isEqualTo(location);
            });
            assertThat(capture.sql).contains("st_dwithin(n.geo_point,ml.geo_point,n.search_radius_meters)");
            explainNamed("04-need-geo-radius", capture);

            var selected = discovery.findCandidateForNeed(merchant, location, need, now);
            assertThat(selected).hasSize(1);
            assertThat(selected.getFirst().needId()).isEqualTo(need);
            assertThat(selected.getFirst().categoryId()).isEqualTo(id("pilot-category-1"));
            assertThat(selected.getFirst().type().name()).isEqualTo("PRODUCT");
            assertThat(capture.sql).contains("ci.item_type=n.need_type", "cp.ancestor_id=n.category_id");
            explainNamed("05-need-category-type", capture);

            var relevance = discovery.findCandidates(merchant, Set.of(location), location, now, null, 31);
            assertThat(relevance).hasSize(31).allSatisfy(row -> assertThat(row.textRank()).isPositive());
            assertThat(capture.sql).contains("ts_rank", "websearch_to_tsquery", "similarity", "n.query_text");
            explainNamed("06-catalog-text-trigram-against-need", capture);

            SqlCapture.SQL.remove();
            assertThat(responses.findByNeedRequestIdAndMerchantLocationIdAndResponseStatus(
                    need, location, NeedResponseStatus.ACTIVE)).isPresent();
            explain("07-active-response-lookup", SqlCapture.SQL.get(), need, location, "ACTIVE");

            SqlCapture.SQL.remove();
            var page = responses.findPage(need, NeedResponseStatus.ACTIVE, null, null, PageRequest.of(0, 21));
            assertThat(page).hasSize(21);
            explain("08-buyer-response-list", SqlCapture.SQL.get(), need, "ACTIVE", 21);

            SqlCapture.SQL.remove();
            assertThat(items.findByNeedResponseIdInAndItemStatusOrderByCreatedAtAsc(
                    List.of(page.getFirst().getId()), vn.covie.needs.model.NeedResponseItemStatus.ACTIVE)).hasSize(1);
            explain("09-buyer-response-items", SqlCapture.SQL.get(), page.getFirst().getId(), "ACTIVE");
        } finally {
            SqlCapture.SQL.remove();
        }
    }

    private UUID id(String value) {
        return jdbc.queryForObject("select md5(?)::uuid", UUID.class, value);
    }

    private void explain(String name, String sql, Object... parameters) throws Exception {
        assertThat(sql).isNotBlank();
        assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(parameters.length);
        String plan = jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, String.class, parameters);
        record(name, sql, java.util.Arrays.toString(parameters), plan);
    }

    private void explainNamed(String name, NamedCapture capture) throws Exception {
        String plan = new NamedParameterJdbcTemplate(jdbc.getDataSource()).queryForObject(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + capture.sql, capture.parameters, String.class);
        record(name, capture.sql, capture.parameters.toString(), plan);
    }

    private void record(String name, String sql, String bindings, String plan) throws Exception {
        var root = new ObjectMapper().readTree(plan);
        assertThat(root.isArray()).isTrue();
        assertThat(root.get(0).path("Plan").path("Actual Rows").asLong()).isPositive();
        assertThat(root.get(0).path("Execution Time").isNumber()).isTrue();
        assertThat(root.get(0).path("Plan").has("Shared Hit Blocks")).isTrue();
        Path directory = Path.of("../../.local/need-original-plans-20261020");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".sql"), sql);
        Files.writeString(directory.resolve(name + "-bindings.txt"), bindings);
        Files.writeString(directory.resolve(name + "-plan.json"), plan);
    }

    private static class NamedCapture extends NamedParameterJdbcTemplate {
        private String sql;
        private SqlParameterSource parameters;
        NamedCapture(DataSource source) { super(source); }
        @Override public <T> List<T> query(String sql, SqlParameterSource parameters, RowMapper<T> mapper) {
            this.sql = sql;
            this.parameters = parameters;
            return super.query(sql, parameters, mapper);
        }
    }
}
