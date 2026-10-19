package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SocialPopulatedMigrationUpgradeTest {
    @Test void upgradesV013UserAndAddressDataToLatestWithoutChangingExistingValues() {
        try(var postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("migration_upgrade_test").withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            var initial=Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("013").load();
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
            var beforeCaption=Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("019").load();
            assertThat(beforeCaption.migrate().migrationsExecuted).isEqualTo(6);
            UUID category=UUID.randomUUID();
            jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Existing category',?,'BOTH','ACTIVE')",category,"caption-upgrade-"+category);
            UUID firstItem=null;
            for (String kind : java.util.List.of("PRODUCT","SERVICE","RESALE")) {
                UUID item=UUID.randomUUID(),variant=UUID.randomUUID(),offer=UUID.randomUUID();
                if (firstItem==null) firstItem=item;
                String type=kind.equals("SERVICE")?"SERVICE":"PRODUCT";
                String listingKind=kind.equals("RESALE")?"RESALE":"STANDARD";
                String status=kind.equals("SERVICE")?"PAUSED":kind.equals("RESALE")?"DRAFT":"ACTIVE";
                String moderation=kind.equals("RESALE")?"NOT_REQUIRED":"APPROVED";
                jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,listing_status,moderation_status,search_vector,created_at,updated_at,version) "
                        +"values (?,?,?,?,?,'Existing item',?,'Existing commercial description',?,?,to_tsvector('simple','existing item'),now(),now(),7)",
                        item,merchant,type,listingKind,category,"upgrade-item-"+item,status,moderation);
                jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,'Default',true,'ACTIVE',now(),now(),3)",variant,item);
                jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values (?,?,?,31000,'VND',true,?,now(),now(),4)",
                        offer,location,variant,kind.equals("SERVICE")?null:kind.equals("PRODUCT")?"TRACKED":"UNTRACKED");
                if (kind.equals("PRODUCT")) jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,5,2,now(),8)",offer);
                for (int position : new int[]{0,5}) {
                    UUID media=UUID.randomUUID();
                    jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) "
                            +"values (?,?,'MINIO',?,'CATALOG_IMAGE','image/jpeg',128,'READY',now()+interval '1 hour',now(),now())",media,user,"upgrade-media-"+media);
                    jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',?,?)",item,media,position,position==0);
                }
            }
            jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind) values (?,?,'LIKE'),(?,?,'SAVE')",firstItem,user,firstItem,user);
            jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,status) values (?,?,?,'Existing comment','ACTIVE')",UUID.randomUUID(),firstItem,user);
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'EXISTING_CASE','OPEN',now())",UUID.randomUUID(),firstItem);
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_user_id,reason_code,case_status,opened_at) values (?,'USER_REPORT','USER',?,'EXISTING_USER_CASE','RESOLVED',now())",UUID.randomUUID(),user);
            var beforeBusiness=businessRows(jdbc);
            Long beforeHeap=jdbc.queryForObject("select relfilenode::bigint from pg_class where oid='catalog_items'::regclass",Long.class);
            var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("020").load();
            long migrationStarted=System.nanoTime();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            System.out.println("V019->V020 populated local migration elapsed milliseconds: "+java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-migrationStarted));
            latest.validate();
            assertThat(latest.info().current().getVersion().toString()).isEqualTo("020");
            assertThat(businessRows(jdbc)).isEqualTo(beforeBusiness);
            assertThat(jdbc.queryForObject("select relfilenode::bigint from pg_class where oid='catalog_items'::regclass",Long.class)).isEqualTo(beforeHeap);
            assertThat(jdbc.queryForObject("select count(*) from catalog_items where social_caption is null",Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForMap("select data_type,is_nullable from information_schema.columns where table_schema='public' and table_name='catalog_items' and column_name='social_caption'"))
                    .isEqualTo(java.util.Map.of("data_type","text","is_nullable","YES"));
            assertThat(jdbc.queryForObject("select column_default from information_schema.columns where table_schema='public' and table_name='catalog_items' and column_name='social_caption'",String.class)).isNull();
            assertThat(jdbc.queryForMap("select id,display_name,account_status,created_at,updated_at,version from users where id=?",user)).isEqualTo(beforeUser);
            assertThat(jdbc.queryForObject("select phone from users where id=?",String.class,user)).isNull();
            assertThat(jdbc.queryForMap("select id,user_id,label,recipient_name,phone,address_text,ST_AsText(geo_point::geometry) as point,pin_source,is_default,status,created_at,updated_at,version from user_addresses where id=?",address)).isEqualTo(beforeAddress);
            assertThat(jdbc.queryForMap("select * from orders where id=?",order)).isEqualTo(beforeOrder);
            assertThat(jdbc.queryForMap("select "+fulfillmentColumns+" from service_fulfillments where order_id=?",order)).isEqualTo(beforeFulfillment);
            assertThat(jdbc.queryForObject("select distance_meters from service_fulfillments where order_id=?",Integer.class,order)).isNull();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            var indexed=Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("021").load();
            assertThat(indexed.migrate().migrationsExecuted).isEqualTo(1);
            indexed.validate();
            assertThat(indexed.info().current().getVersion().toString()).isEqualTo("021");
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname='ix_moderation_cases_catalog_recent'",String.class))
                    .contains("(target_catalog_item_id, opened_at DESC, id DESC)","WHERE (target_catalog_item_id IS NOT NULL)");
            assertThat(businessRows(jdbc)).isEqualTo(beforeBusiness);
            assertThat(jdbc.queryForObject("select relfilenode::bigint from pg_class where oid='catalog_items'::regclass",Long.class)).isEqualTo(beforeHeap);
            assertThat(indexed.migrate().migrationsExecuted).isZero();
            var offersIndexed=Flyway.configure().dataSource(source).locations("classpath:db/migration-social")
                    .configuration(java.util.Map.of("flyway.postgresql.transactional.lock","false")).target("022").load();
            Long beforeOfferHeap=jdbc.queryForObject("select relfilenode::bigint from pg_class where oid='location_variants'::regclass",Long.class);
            assertThat(offersIndexed.migrate().migrationsExecuted).isEqualTo(1);
            offersIndexed.validate();
            assertThat(offersIndexed.info().current().getVersion().toString()).isEqualTo("022");
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname='ix_location_variant_variant'",String.class))
                    .contains("ON public.location_variants USING btree (variant_id)").doesNotContain("WHERE");
            assertThat(jdbc.queryForObject("select indisvalid and indisready from pg_index where indexrelid='ix_location_variant_variant'::regclass",Boolean.class)).isTrue();
            assertThat(businessRows(jdbc)).isEqualTo(beforeBusiness);
            assertThat(jdbc.queryForObject("select relfilenode::bigint from pg_class where oid='location_variants'::regclass",Long.class)).isEqualTo(beforeOfferHeap);
            assertThat(offersIndexed.migrate().migrationsExecuted).isZero();
            var videoExpanded=Flyway.configure().dataSource(source).locations("classpath:db/migration-social")
                    .configuration(java.util.Map.of("flyway.postgresql.transactional.lock","false")).target("025").load();
            assertThat(videoExpanded.migrate().migrationsExecuted).isEqualTo(3);
            videoExpanded.validate();
            assertThat(videoExpanded.info().current().getVersion().toString()).isEqualTo("025");
            assertThat(businessRows(jdbc)).isEqualTo(beforeBusiness);
            assertThat(jdbc.queryForObject("select count(*) from media_assets where video_duration_ms is not null or video_codec is not null or storage_cleanup_attempted_at is not null or storage_deleted_at is not null",Long.class)).isZero();
            assertThat(videoExpanded.migrate().migrationsExecuted).isZero();
        }
    }

    private java.util.Map<String,java.util.List<String>> businessRows(JdbcTemplate jdbc) {
        var snapshot=new java.util.LinkedHashMap<String,java.util.List<String>>();
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename<>'flyway_schema_history' order by tablename",String.class)) {
            String relation="\""+table.replace("\"","\"\"")+"\"";
            String projection=table.equals("catalog_items")?"(to_jsonb(original)-'social_caption')":table.equals("media_assets")
                    ?"(to_jsonb(original)-'video_duration_ms'-'video_codec'-'storage_cleanup_attempted_at'-'storage_deleted_at')":"to_jsonb(original)";
            snapshot.put(table,jdbc.queryForList("select "+projection+"::text from "+relation+" original order by 1",String.class));
        }
        return snapshot;
    }
}
