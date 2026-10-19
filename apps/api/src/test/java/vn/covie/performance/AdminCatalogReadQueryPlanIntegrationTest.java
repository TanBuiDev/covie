package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;

@Tag("performance")
@EnabledIfSystemProperty(named="covie.test.admin-read-plan",matches="true")
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.admin-reads-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.performance.AdminCatalogReadQueryPlanIntegrationTest$SqlCapture"})
@Transactional
class AdminCatalogReadQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<List<String>> SQL=ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) {
            if (sql.startsWith("select ") && sql.contains(" from media_assets ")) SQL.get().add(sql);
            return sql;
        }
    }

    @Test void capturesExecutedListAndDetailPlansWithBoundedMediaBatches() throws Exception {
        jdbc.update("""
                insert into users(id,display_name,account_status,created_at,updated_at,version)
                values(md5('admin-read-plan-user')::uuid,'Read plan admin','ACTIVE',now(),now(),0)
                """);
        jdbc.update("""
                insert into system_role_assignments(id,user_id,role,created_at)
                values(md5('admin-read-plan-role')::uuid,md5('admin-read-plan-user')::uuid,'ADMIN',now())
                """);
        jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                select md5('admin-read-plan-shop-'||g)::uuid,'Read shop '||g,'admin-read-plan-shop-'||g,
                'ACTIVE','APPROVED',now(),now(),0 from generate_series(1,1000) g
                """);
        jdbc.update("""
                insert into categories(id,name,slug,catalog_type,status,sort_order,version)
                values(md5('admin-read-plan-category')::uuid,'Read category','admin-read-plan-category','PRODUCT','ACTIVE',0,0)
                """);
        jdbc.update("""
                insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                description,social_caption,listing_status,moderation_status,created_at,updated_at,version)
                select md5('admin-read-plan-item-'||g)::uuid,md5('admin-read-plan-shop-'||((g-1)%1000+1))::uuid,
                'PRODUCT','STANDARD',md5('admin-read-plan-category')::uuid,'Read item '||g,'admin-read-plan-item-'||g,
                'Commercial description',repeat('x',3000),'ACTIVE','APPROVED',
                now()-g*interval '1 second',now()-g*interval '1 second',0 from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,
                media_status,upload_expires_at,verified_at,created_at)
                select md5('admin-read-plan-media-'||g)::uuid,'S3','admin-read-plan-media-'||g,'CATALOG_IMAGE','image/jpeg',128,
                'READY',now()+interval '1 hour',now(),now() from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary)
                select md5('admin-read-plan-item-'||((g-1)/10+1))::uuid,md5('admin-read-plan-media-'||g)::uuid,
                'IMAGE',(g-1)%10,(g-1)%10=0 from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at)
                select md5('admin-read-plan-variant-'||g)::uuid,md5('admin-read-plan-item-'||g)::uuid,
                'Default',true,'ACTIVE',now(),now() from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,
                location_status,pickup_enabled,currency_code,created_at,updated_at)
                select md5('admin-read-plan-location-'||g)::uuid,md5('admin-read-plan-shop-'||((g-1)/3+1))::uuid,
                'Read location '||g,'1 Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),
                'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now() from generate_series(1,3000) g
                """);
        jdbc.update("""
                insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,
                inventory_mode,preparation_time_minutes,created_at,updated_at)
                select md5('admin-read-plan-offer-'||g||'-'||slot)::uuid,
                md5('admin-read-plan-location-'||(((g-1)%1000)*3+slot))::uuid,
                md5('admin-read-plan-variant-'||g)::uuid,25000,'VND',true,'UNTRACKED',0,now(),now()
                from generate_series(1,20000) g cross join generate_series(1,3) slot
                """);
        for (String table:List.of("catalog_items","merchant_profiles","categories","media_assets","catalog_item_media",
                "variants","merchant_locations","location_variants")) jdbc.execute("analyze "+table);
        var output=Path.of(System.getProperty("covie.test.admin-read-plan-output","target/admin-read-plans"));
        Files.createDirectories(output);
        var measurements=new ArrayList<String>();
        UUID actor=jdbc.queryForObject("select md5('admin-read-plan-user')::uuid",UUID.class);
        UUID item=jdbc.queryForObject("select md5('admin-read-plan-item-1')::uuid",UUID.class);
        UUID merchant=jdbc.queryForObject("select md5('admin-read-plan-shop-1')::uuid",UUID.class);
        String token="Bearer "+issuer.issue(actor,new DeviceMetadata("Test","Admin read plans")).accessToken().value();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            for (String label:List.of("default-20","maximum-100","filtered-search","detail")) {
                boolean detail=label.equals("detail");
                var request=get("/api/v1/admin/catalog-items"+(detail?"/"+item:"" )).header("Authorization",token);
                int expected=label.equals("maximum-100")?100:20;
                if (!detail) request.param("size",Integer.toString(expected));
                if (label.equals("filtered-search")) request.param("merchantId",merchant.toString()).param("type","PRODUCT")
                        .param("listingStatus","ACTIVE").param("moderationStatus","APPROVED").param("q","Read item").param("sort","CREATED_ASC");
                org.mockito.Mockito.clearInvocations(named);
                SqlCapture.SQL.get().clear();
                long started=System.nanoTime();
                var response=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
                double elapsedMs=(System.nanoTime()-started)/1_000_000.0;
                var body=mapper.readTree(response.getContentAsString());
                var assetIds=new ArrayList<UUID>();
                if (detail) {
                    assertThat(body.path("media")).hasSize(10);
                    assertThat(body.path("locations")).hasSize(3);
                    assertThat(body.path("socialCaption").asText()).hasSize(3000);
                    assertThat(body.path("item").path("id").asText()).isEqualTo(item.toString());
                    for (var media:body.path("media")) assetIds.add(UUID.fromString(media.path("id").asText()));
                } else {
                    assertThat(body.path("items")).hasSize(expected);
                    assertThat(body.path("total").asInt()).isEqualTo(label.equals("filtered-search")?20:20000);
                    for (var entry:body.path("items")) assetIds.add(UUID.fromString(entry.path("primaryMedia").path("id").asText()));
                }
                var calls=org.mockito.Mockito.mockingDetails(named).getInvocations().stream().filter(call -> {
                    Object[] arguments=call.getArguments();
                    if (arguments.length<2 || !(arguments[0] instanceof String sql) || !sql.stripLeading().startsWith("select")
                            || !(arguments[1] instanceof SqlParameterSource)) return false;
                    return call.getMethod().getName().equals("queryForMap") && arguments.length==2
                            || arguments.length==3 && (call.getMethod().getName().equals("queryForObject") && arguments[2] instanceof Class<?>
                            || call.getMethod().getName().equals("query") && arguments[2] instanceof RowMapper<?>);
                }).toList();
                assertThat(calls).as(label+" named statement count").hasSize(detail?6:2);
                assertThat(SqlCapture.SQL.get()).as(label+" asset select count").hasSize(1);
                for (int query=0;query<calls.size();query++) {
                    String sql=calls.get(query).getArgument(0);
                    SqlParameterSource parameters=calls.get(query).getArgument(1);
                    String plan=named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,parameters,String.class);
                    Files.writeString(output.resolve(label+"-"+query+".sql"),sql);
                    Files.writeString(output.resolve(label+"-"+query+".json"),plan);
                    measurements.add(label+" query="+query+" executionMs="+mapper.readTree(plan).get(0).path("Execution Time").asDouble());
                }
                String assetSql=SqlCapture.SQL.get().getFirst();
                assertThat(assetSql.chars().filter(character -> character=='?').count()).isEqualTo(assetIds.size());
                String assetPlan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+assetSql,String.class,assetIds.toArray());
                Files.writeString(output.resolve(label+"-assets.sql"),assetSql);
                Files.writeString(output.resolve(label+"-assets.json"),assetPlan);
                measurements.add(label+" assetMs="+mapper.readTree(assetPlan).get(0).path("Execution Time").asDouble()
                        +" namedQueries="+calls.size()+" assetQueries=1 returnedAssets="+assetIds.size()
                        +" requestMs="+elapsedMs+" responseBytes="+response.getContentAsByteArray().length);
                Files.write(output.resolve("measurements.txt"),measurements);
                if (detail) assertVariantScopedLocations(mapper.readTree(Files.readString(output.resolve("detail-3.json"))).get(0).path("Plan"));
            }
        } finally { SqlCapture.SQL.remove(); }
    }

    private void assertVariantScopedLocations(com.fasterxml.jackson.databind.JsonNode node) {
        if ("location_variants".equals(node.path("Relation Name").asText()))
            assertThat(node.path("Node Type").asText()).as("one item's locations must not scan all other items' offers").isNotEqualTo("Seq Scan");
        for (var child:node.path("Plans")) assertVariantScopedLocations(child);
    }
}
