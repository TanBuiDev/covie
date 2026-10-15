package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class PopulatedMigrationUpgradeTest {
    @Test void upgradesV013UserAndAddressDataToLatestWithoutChangingExistingValues() {
        try(var postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("migration_upgrade_test").withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            var initial=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("013").load();
            initial.migrate();
            var jdbc=new JdbcTemplate(source);
            UUID user=UUID.randomUUID(),address=UUID.randomUUID();
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Existing user','ACTIVE',now(),now())",user);
            jdbc.update("insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version) values (?,?,'Home','Existing recipient','0900000001','Existing street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS',true,'ACTIVE',now(),now(),0)",address,user);
            var beforeUser=jdbc.queryForMap("select id,display_name,account_status,created_at,updated_at,version from users where id=?",user);
            var beforeAddress=jdbc.queryForMap("select id,user_id,label,recipient_name,phone,address_text,ST_AsText(geo_point::geometry) as point,pin_source,is_default,status,created_at,updated_at,version from user_addresses where id=?",address);
            UUID merchant=UUID.randomUUID(),location=UUID.randomUUID(),order=UUID.randomUUID();
            jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,'Existing shop',?,'ACTIVE','APPROVED',now(),now(),0)",merchant,"upgrade-"+merchant);
            jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,created_at,updated_at,version) values (?,?,'Existing provider','Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',false,false,true,0,'VND',now(),now(),0)",location,merchant);
            jdbc.update("insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,payment_method,subtotal,shipping_fee_type,known_total,total_amount,currency_code,confirmation_expires_at,created_at,updated_at,version) values (?,?,'SERVICE',?,?,?,'PENDING_CONFIRMATION','MARKETPLACE','COD',10000,'NOT_APPLICABLE',10000,10000,'VND',now()+interval '10 minutes',now(),now(),0)",order,"UPGRADE-"+order.toString().substring(0,8),user,merchant,location);
            jdbc.update("insert into service_fulfillments(order_id,service_address_id,service_address_snapshot,provider_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,created_at) values (?,?,'{\"address\":\"historic street\"}'::jsonb,'{\"name\":\"historic provider\"}'::jsonb,'{\"phone\":\"0900000001\"}'::jsonb,'{}'::jsonb,now())",order,address);
            var beforeOrder=jdbc.queryForMap("select * from orders where id=?",order);
            String fulfillmentColumns="order_id,service_address_id,service_address_snapshot::text,provider_location_snapshot::text,buyer_contact_snapshot::text,merchant_contact_snapshot::text,created_at,provider_marked_performed_at,awaiting_buyer_confirmation_at,auto_complete_at";
            var beforeFulfillment=jdbc.queryForMap("select "+fulfillmentColumns+" from service_fulfillments where order_id=?",order);
            var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(11);
            latest.validate();
            assertThat(latest.info().current().getVersion().toString()).isEqualTo("024");
            assertThat(jdbc.queryForMap("select id,display_name,account_status,created_at,updated_at,version from users where id=?",user)).isEqualTo(beforeUser);
            assertThat(jdbc.queryForObject("select phone from users where id=?",String.class,user)).isNull();
            assertThat(jdbc.queryForMap("select id,user_id,label,recipient_name,phone,address_text,ST_AsText(geo_point::geometry) as point,pin_source,is_default,status,created_at,updated_at,version from user_addresses where id=?",address)).isEqualTo(beforeAddress);
            assertThat(jdbc.queryForMap("select * from orders where id=?",order)).isEqualTo(beforeOrder);
            assertThat(jdbc.queryForMap("select "+fulfillmentColumns+" from service_fulfillments where order_id=?",order)).isEqualTo(beforeFulfillment);
            assertThat(jdbc.queryForObject("select distance_meters from service_fulfillments where order_id=?",Integer.class,order)).isNull();
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
