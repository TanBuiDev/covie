package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan06SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void v011CreatesNeedSchemaAndSourceConstraints() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(currentFlywayVersion(connection)).isEqualTo("014");
            for (String table : new String[] {"need_requests", "need_responses", "need_response_items"}) {
                assertThat(objectExists(connection, table)).as(table).isTrue();
            }
            for (String index : new String[] {
                "ux_need_response_active_location", "ux_need_response_active_location_variant",
                "ix_need_requests_geo", "ix_need_requests_buyer_created",
                "ix_need_requests_status_expiry", "ix_need_requests_category_type_state",
                "ix_need_responses_need_state", "ix_need_response_items_response_state" }) {
                assertThat(objectExists(connection, index)).as(index).isTrue();
            }
            assertThat(constraintExists(connection, "orders", "fk_orders_source_need_request")).isTrue();
            assertThat(constraintExists(connection, "orders", "fk_orders_source_need_response_item")).isTrue();
            assertThat(constraintExists(connection, "orders", "ck_order_source_payload")).isTrue();
        }
    }

    @Test
    void retainsRetiredResponseItemsReferencedByOrders() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID catalogItem = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID locationVariant = UUID.randomUUID();
        UUID need = UUID.randomUUID();
        UUID response = UUID.randomUUID();
        UUID responseItem = UUID.randomUUID();
        UUID order = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "insert into users(id,display_name,account_status,created_at,updated_at,version) values ('%s','buyer','ACTIVE',now(),now(),0)".formatted(buyer));
            execute(connection, "insert into users(id,display_name,account_status,created_at,updated_at,version) values ('%s','seller','ACTIVE',now(),now(),0)".formatted(seller));
            execute(connection, "insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values ('%s','Merchant','merchant-%s','ACTIVE','APPROVED',now(),now(),0)".formatted(merchant, merchant));
            execute(connection, "insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version) values ('%s','%s','Main','1 Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,'VND',now(),now(),0)".formatted(location, merchant));
            execute(connection, "insert into categories(id,name,slug,catalog_type,status,sort_order,version) values ('%s','Food','food-%s','PRODUCT','ACTIVE',0,0)".formatted(category, category));
            execute(connection, "insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values ('%s','%s','PRODUCT','STANDARD','%s','Rice','rice-%s','ACTIVE','APPROVED',now(),now(),0)".formatted(catalogItem, merchant, category, catalogItem));
            execute(connection, "insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values ('%s','%s','Default',true,'ACTIVE',now(),now(),0)".formatted(variant, catalogItem));
            execute(connection, "insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values ('%s','%s','%s',10000,'VND',true,'UNTRACKED',now(),now(),0)".formatted(locationVariant, location, variant));
            execute(connection, "insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,geo_point,location_snapshot,search_radius_meters,request_status,expires_at,created_at,updated_at) values ('%s','%s','PRODUCT','%s','Rice','rice',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'{}',1000,'ACTIVE',now() + interval '1 hour',now(),now())".formatted(need, buyer, category));
            execute(connection, "insert into need_responses(id,need_request_id,merchant_profile_id,merchant_location_id,response_status,created_by_user_id,created_at,updated_at,version) values ('%s','%s','%s','%s','ACTIVE','%s',now(),now(),0)".formatted(response, need, merchant, location, seller));
            execute(connection, "insert into need_response_items(id,need_response_id,catalog_item_id,variant_id,location_variant_id,item_status,price_snapshot,currency_code,availability_snapshot,created_at) values ('%s','%s','%s','%s','%s','ACTIVE',10000,'VND','{}',now())".formatted(responseItem, response, catalogItem, variant, locationVariant));
            execute(connection, "insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,source_need_request_id,source_need_response_item_id,payment_method,subtotal,known_total,currency_code,created_at,updated_at,version) values ('%s','order-%s','PRODUCT','%s','%s','%s','PENDING_CONFIRMATION','NEED_REQUEST','%s','%s','COD',10000,10000,'VND',now(),now(),0)".formatted(order, order.toString().substring(0, 8), buyer, merchant, location, need, responseItem));
            execute(connection, "update need_response_items set item_status = 'RETIRED', retired_at = now() where id = '%s'".formatted(responseItem));

            assertThatThrownBy(() -> execute(connection, "delete from need_response_items where id = '%s'".formatted(responseItem)))
                    .isInstanceOf(Exception.class);
        }
    }

    private String currentFlywayVersion(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                select version from flyway_schema_history
                where success = true order by installed_rank desc limit 1
                """)) {
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getString(1);
            }
        }
    }

    private boolean objectExists(Connection connection, String name) throws Exception {
        try (var statement = connection.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            statement.setString(1, name);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getBoolean(1);
            }
        }
    }

    private boolean constraintExists(Connection connection, String table, String constraint) throws Exception {
        try (var statement = connection.prepareStatement("""
                select 1 from pg_constraint pc
                join pg_class t on t.oid = pc.conrelid
                where t.relname = ? and pc.conname = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
