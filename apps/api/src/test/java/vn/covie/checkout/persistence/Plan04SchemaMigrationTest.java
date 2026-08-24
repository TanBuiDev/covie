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
            assertThat(currentFlywayVersion(c)).isGreaterThanOrEqualTo("010");
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
