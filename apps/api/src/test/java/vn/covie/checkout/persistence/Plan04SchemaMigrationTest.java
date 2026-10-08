package vn.covie.checkout.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan04SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void appliesPlan04MigrationsAndCreatesAllCreationTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(currentFlywayVersion(c)).isGreaterThanOrEqualTo("014");
            for (String table : new String[] {
                "delivery_coverages", "shipping_policies", "inventory_balances",
                "inventory_reservations", "inventory_movements", "carts", "cart_items",
                "checkout_sessions", "orders", "order_items", "product_fulfillments",
                "service_fulfillments", "order_events" }) {
                assertThat(tableExists(c, table)).as(table).isTrue();
            }
        }
    }

    @Test
    void createsRequiredPlan04Indexes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            for (String index : new String[] {
                "ux_delivery_coverage_active_type", "ux_shipping_policy_active_location",
                "ux_cart_active_user_location_type", "ux_inventory_reservation_active_order_item",
                "ix_delivery_coverages_location_type_status", "ix_delivery_coverage_areas_area",
                "ix_shipping_rules_policy_enabled_priority", "ix_inventory_reservations_order_status",
                "ix_inventory_reservations_variant_status", "ix_inventory_movements_variant_created",
                "ix_cart_items_cart", "ix_orders_buyer_created", "ix_orders_merchant_created",
                "ix_order_items_order", "ix_order_events_order_created" }) {
                assertThat(indexExists(c, index)).as(index).isTrue();
            }
        }
    }

    @Test
    void enforcesInventoryCartAndReservationDatabaseInvariants() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> c.createStatement().execute(
                    "insert into inventory_balances(location_variant_id, stock_on_hand, reserved_quantity, updated_at) "
                        + "values ('00000000-0000-0000-0000-000000000001', 0, 1, now())"))
                .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> c.createStatement().execute(
                    "insert into cart_items(id, cart_id, location_variant_id, variant_id, quantity, price_snapshot, currency_code, item_name_snapshot, added_at, updated_at) "
                        + "values ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 0, 1, 'VND', 'x', now(), now())"))
                .isInstanceOf(Exception.class);
        }
    }

    @Test
    void addsNullableNonNegativeServiceDistance() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var ps = c.prepareStatement("select data_type, is_nullable from information_schema.columns where table_schema='public' and table_name='service_fulfillments' and column_name='distance_meters'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("data_type")).isEqualTo("integer");
                    assertThat(rs.getString("is_nullable")).isEqualTo("YES");
                }
            }
            try (var ps = c.prepareStatement("select pg_get_constraintdef(oid) from pg_constraint where conname='ck_service_fulfillment_distance'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).contains("distance_meters >= 0");
                }
            }

            String buyer = java.util.UUID.randomUUID().toString();
            String merchant = java.util.UUID.randomUUID().toString();
            String location = java.util.UUID.randomUUID().toString();
            String order = java.util.UUID.randomUUID().toString();
            c.createStatement().execute("insert into users(id,display_name,account_status,created_at,updated_at,version) values ('"+buyer+"','Buyer','ACTIVE',now(),now(),0)");
            c.createStatement().execute("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values ('"+merchant+"','Merchant','merchant-"+merchant+"','ACTIVE','NOT_REQUIRED',now(),now(),0)");
            c.createStatement().execute("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version) values ('"+location+"','"+merchant+"','Main','1 Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',false,false,true,'VND',now(),now(),0)");
            c.createStatement().execute("insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,payment_method,subtotal,shipping_fee_type,known_total,total_amount,currency_code,created_at,updated_at,version) values ('"+order+"','CV"+order.substring(0,8)+"','SERVICE','"+buyer+"','"+merchant+"','"+location+"','PENDING_CONFIRMATION','MARKETPLACE','COD',100,'NOT_APPLICABLE',100,100,'VND',now(),now(),0)");
            assertThatThrownBy(() -> c.createStatement().execute("insert into service_fulfillments(order_id,service_address_id,distance_meters,service_address_snapshot,provider_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,created_at) values ('"+order+"',null,-1,'{}','{}','{}','{}',now())"))
                    .isInstanceOf(Exception.class);
        }
    }

    private String currentFlywayVersion(Connection c) throws Exception {
        try (var ps = c.prepareStatement("select version from flyway_schema_history where success = true order by installed_rank desc limit 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private boolean tableExists(Connection c, String name) throws Exception {
        return objectExists(c, name);
    }

    private boolean indexExists(Connection c, String name) throws Exception {
        return objectExists(c, name);
    }

    private boolean objectExists(Connection c, String name) throws Exception {
        try (var ps = c.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
