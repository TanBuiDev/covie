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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;

@Tag("performance")
@EnabledIfSystemProperty(named="covie.test.social-activity-plan",matches="true")
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.activity-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.performance.SocialActivityQueryPlanIntegrationTest$SqlCapture"})
@Transactional
class SocialActivityQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<List<String>> SQL=ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) {
            if (sql.startsWith("select ")) SQL.get().add(sql);
            return sql;
        }
    }

    @Test void capturesExactPrivateActivityPlansAndBoundedDataQueries() throws Exception {
        jdbc.update("""
                insert into users(id,display_name,account_status,created_at,updated_at,version)
                select md5('activity-plan-user-'||g)::uuid,'Activity buyer '||g,'ACTIVE',now(),now(),0 from generate_series(1,1000) g
                """);
        jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                select md5('activity-plan-merchant-'||g)::uuid,'Activity merchant '||g,'activity-plan-merchant-'||g,
                'ACTIVE','APPROVED',now(),now(),0 from generate_series(1,1000) g
                """);
        jdbc.update("""
                insert into categories(id,name,slug,catalog_type,status,sort_order,version)
                values(md5('activity-plan-category')::uuid,'Activity category','activity-plan-category','PRODUCT','ACTIVE',0,0)
                """);
        jdbc.update("""
                insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                listing_status,moderation_status,created_at,updated_at,version)
                select md5('activity-plan-item-'||g)::uuid,md5('activity-plan-merchant-'||((g-1)%1000+1))::uuid,
                'PRODUCT','STANDARD',md5('activity-plan-category')::uuid,'Activity item '||g,'activity-plan-item-'||g,
                'ACTIVE','APPROVED',now(),now(),0 from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,
                media_status,upload_expires_at,verified_at,created_at)
                select md5('activity-plan-item-'||g)::uuid,'S3','activity-plan-media-'||g,'CATALOG_IMAGE','image/jpeg',128,
                'READY',now()+interval '1 hour',now(),now() from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary)
                select md5('activity-plan-item-'||g)::uuid,md5('activity-plan-item-'||g)::uuid,'IMAGE',0,true from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into catalog_social_reactions(catalog_item_id,user_id,kind,created_at)
                select md5('activity-plan-item-'||((g-1)%20000+1))::uuid,md5('activity-plan-user-'||((g-1)/200+1))::uuid,
                case when g%2=0 then 'LIKE' else 'SAVE' end,now()-((g-1)%200)*interval '1 second' from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,status,created_at,updated_at)
                select md5('activity-plan-comment-'||g)::uuid,md5('activity-plan-item-'||((g-1)%20000+1))::uuid,
                md5('activity-plan-user-'||((g-1)/200+1))::uuid,repeat('x',1000),'ACTIVE',
                now()-((g-1)%200)*interval '1 second',now() from generate_series(1,200000) g
                """);
        for (String table : List.of("catalog_items","merchant_profiles","catalog_item_media","media_assets","catalog_social_reactions","catalog_social_comments"))
            jdbc.execute("analyze "+table);
        for (String table : List.of("catalog_social_reactions","catalog_social_comments"))
            assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).isGreaterThanOrEqualTo(200000);
        UUID actor=jdbc.queryForObject("select md5('activity-plan-user-1')::uuid",UUID.class);
        String token="Bearer "+issuer.issue(actor,new DeviceMetadata("Test","Activity plans")).accessToken().value();
        var output=Path.of(System.getProperty("covie.test.social-activity-plan-output","target/social-activity-plans"));
        Files.createDirectories(output);
        var measurements=new ArrayList<String>();
        try {
            for (String type : List.of("LIKED","SAVED","COMMENTED")) {
                String next=null;
                for (int step=0;step<3;step++) {
                    int limit=step==0?20:50;
                    org.mockito.Mockito.clearInvocations(named); SqlCapture.SQL.get().clear();
                    var request=get("/api/v1/me/social/activity").header("Authorization",token).param("type",type);
                    if (step>0) request.param("limit","50");
                    if (step==2) request.param("cursor",next);
                    long started=System.nanoTime();
                    var response=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
                    double elapsedMs=(System.nanoTime()-started)/1_000_000.0;
                    var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
                    assertThat(body.path("items")).hasSize(limit);
                    next=body.path("nextCursor").asText();
                    var mediaIds=new ArrayList<UUID>();
                    for (var entry : body.path("items")) {
                        assertThat(entry.path("coverUrl").asText()).startsWith("https://media.test.covie/");
                        mediaIds.add(UUID.fromString(entry.path("itemId").asText()));
                    }
                    var calls=org.mockito.Mockito.mockingDetails(named).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("query")
                                    && call.getArgument(0) instanceof String sql && sql.contains("catalog_social_")).toList();
                    assertThat(calls).hasSize(1);
                    var assetQueries=SqlCapture.SQL.get().stream().filter(sql -> sql.contains(" from media_assets ")).toList();
                    assertThat(assetQueries).hasSize(1);
                    String dataSql=calls.getFirst().getArgument(0);
                    SqlParameterSource parameters=calls.getFirst().getArgument(1);
                    String assetSql=assetQueries.getFirst();
                    assertThat(assetSql.chars().filter(character -> character=='?').count()).isEqualTo(mediaIds.size()+2);
                    assertThat(assetSql.indexOf("media_status=?")).isLessThan(assetSql.indexOf("upload_purpose=?"));
                    String plan=named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+dataSql,parameters,String.class);
                    var assetParameters=new ArrayList<Object>(mediaIds);
                    assetParameters.add("READY"); assetParameters.add("CATALOG_IMAGE");
                    String assetPlan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+assetSql,String.class,assetParameters.toArray());
                    String label=type.toLowerCase()+"-"+limit+(step==2?"-cursor":"-first");
                    Files.writeString(output.resolve(label+".sql"),dataSql);
                    Files.writeString(output.resolve(label+".json"),plan);
                    Files.writeString(output.resolve(label+"-assets.sql"),assetSql);
                    Files.writeString(output.resolve(label+"-assets.json"),assetPlan);
                    var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(plan).get(0);
                    assertThat(root.path("Plan").path("Actual Rows").asInt()).isBetween(limit,limit+1);
                    assertIndexedLargeRelations(root.path("Plan"));
                    measurements.add(label+" activityDataQueries=1 assetQueries=1 responseBytes="+response.getContentAsByteArray().length
                            +" requestMs="+elapsedMs+" executionMs="+root.path("Execution Time").asDouble());
                }
            }
            Files.write(output.resolve("measurements.txt"),measurements);
        } finally { SqlCapture.SQL.remove(); }
    }

    private void assertIndexedLargeRelations(com.fasterxml.jackson.databind.JsonNode node) {
        if (List.of("catalog_social_reactions","catalog_social_comments","catalog_items","catalog_item_media","media_assets")
                .contains(node.path("Relation Name").asText())) assertThat(node.path("Node Type").asText()).isNotEqualTo("Seq Scan");
        for (var child : node.path("Plans")) assertIndexedLargeRelations(child);
    }
}
