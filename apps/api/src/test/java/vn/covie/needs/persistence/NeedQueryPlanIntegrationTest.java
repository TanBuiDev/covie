package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;

class NeedQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void planSixIndexesBackBuyerExpiryGeoAndResponseAccessPaths() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = current_schema() and tablename in ('need_requests','need_responses','need_response_items')",
                String.class);

        assertThat(indexes).contains(
                "ix_need_requests_buyer_created",
                "ix_need_requests_status_expiry",
                "ix_need_requests_category_type_state",
                "ix_need_requests_geo",
                "ix_need_responses_need_state",
                "ix_need_responses_merchant_location",
                "ux_need_response_active_location",
                "ux_need_response_active_location_variant",
                "ix_need_response_items_response_state");
    }

    @Test
    void expirationAccessPathUsesPartialStatusExpiryIndexWhenSequentialScanIsDisabled() {
        jdbc.execute("set enable_seqscan = off");
        try {
            List<String> plan = jdbc.queryForList(
                    "explain select * from need_requests where request_status='ACTIVE' and expires_at <= now() order by expires_at asc, id asc limit 50",
                    String.class);
            assertThat(String.join("\n", plan)).contains("ix_need_requests_status_expiry");
        } finally {
            jdbc.execute("reset enable_seqscan");
        }
    }
}
