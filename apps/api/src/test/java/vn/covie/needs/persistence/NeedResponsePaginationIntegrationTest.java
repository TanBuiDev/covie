package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.support.PostgresIntegrationTest;

class NeedResponsePaginationIntegrationTest extends PostgresIntegrationTest {
    @Autowired NeedResponseRepository responses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void cursorPagesKeepTimestampTiesOrderedAndExcludeWithdrawnAndOtherNeeds() {
        UUID buyer = UUID.randomUUID(), category = UUID.randomUUID(), merchant = UUID.randomUUID();
        UUID need = UUID.randomUUID(), otherNeed = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T01:00:00Z");
        Timestamp ts = Timestamp.from(now);
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?, 'Buyer','ACTIVE',?,?)", buyer, ts, ts);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order) values (?, 'Test',?,'PRODUCT','ACTIVE',0)", category, "pagination-" + category);
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Test',?,'ACTIVE','NOT_REQUIRED',?,?)", merchant, "pagination-" + merchant, ts, ts);
        for (UUID id : List.of(need, otherNeed)) {
            jdbc.update("""
                insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,geo_point,location_snapshot,
                    search_radius_meters,request_status,expires_at,created_at,updated_at)
                values (?,?,'PRODUCT',?,'Need','query',ST_SetSRID(ST_MakePoint(106.7,10.8),4326)::geography,
                    '{}'::jsonb,5000,'ACTIVE',?,?,?)
                """, id, buyer, category, Timestamp.from(now.plusSeconds(3600)), ts, ts);
        }
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID middle = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID older = UUID.fromString("00000000-0000-0000-0000-000000000004");
        insertResponse(high, need, merchant, buyer, now, false);
        insertResponse(middle, need, merchant, buyer, now, false);
        insertResponse(low, need, merchant, buyer, now, false);
        insertResponse(older, need, merchant, buyer, now.minusSeconds(1), false);
        insertResponse(UUID.randomUUID(), need, merchant, buyer, now.plusSeconds(1), true);
        insertResponse(UUID.randomUUID(), otherNeed, merchant, buyer, now.plusSeconds(1), false);
        var first = responses.findPage(need, NeedResponseStatus.ACTIVE, null, null, PageRequest.of(0, 2));
        assertThat(first).extracting(NeedResponseEntity::getId).containsExactly(high, middle);
        var second = responses.findPage(need, NeedResponseStatus.ACTIVE, now, middle, PageRequest.of(0, 2));
        assertThat(second).extracting(NeedResponseEntity::getId).containsExactly(low, older);
        assertThat(responses.findPage(need, NeedResponseStatus.ACTIVE, now.minusSeconds(1), older, PageRequest.of(0, 2))).isEmpty();
    }

    private void insertResponse(UUID id, UUID need, UUID merchant, UUID buyer, Instant time, boolean withdrawn) {
        UUID location = UUID.randomUUID();
        Timestamp ts = Timestamp.from(time);
        jdbc.update("""
            insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,
                pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at)
            values (?,?,'Location','Test',ST_SetSRID(ST_MakePoint(106.7,10.8),4326)::geography,'GPS','Asia/Ho_Chi_Minh',
                'ACTIVE',true,false,false,'VND',?,?)
            """, location, merchant, ts, ts);
        var response = NeedResponseEntity.active(id, need, merchant, location, buyer, null, time);
        if (withdrawn) response.withdraw(time);
        responses.saveAndFlush(response);
    }

    @Test
    void readsFirstPageWithoutUntypedNullCursor() {
        assertThat(responses.findPage(UUID.randomUUID(), NeedResponseStatus.ACTIVE,
                null, null, PageRequest.of(0, 21))).isEmpty();
    }

    @Test
    void readsCursorPageWithTimestampAndUuidTypes() {
        assertThat(responses.findPage(UUID.randomUUID(), NeedResponseStatus.ACTIVE,
                Instant.parse("2026-09-01T01:00:00Z"), UUID.randomUUID(), PageRequest.of(0, 21))).isEmpty();
    }
}
