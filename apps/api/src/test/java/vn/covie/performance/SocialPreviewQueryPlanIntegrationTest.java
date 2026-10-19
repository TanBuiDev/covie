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
import vn.covie.support.PostgresIntegrationTest;

@Tag("performance")
@EnabledIfSystemProperty(named="covie.test.social-preview-plan",matches="true")
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.previews-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.performance.SocialPreviewQueryPlanIntegrationTest$SqlCapture"})
@Transactional
class SocialPreviewQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<List<String>> SQL=ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) {
            if (sql.startsWith("select ")) SQL.get().add(sql);
            return sql;
        }
    }

    @Test void capturesExactBatchPlansAndConstantQueryCountAtTwentyThousandItems() throws Exception {
        jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                select md5('preview-plan-merchant-'||g)::uuid,'Preview merchant '||g,'preview-plan-merchant-'||g,
                'ACTIVE','APPROVED',now(),now(),0 from generate_series(1,1000) g
                """);
        jdbc.update("""
                insert into categories(id,name,slug,catalog_type,status,sort_order,version)
                values(md5('preview-plan-category')::uuid,'Preview category','preview-plan-category','PRODUCT','ACTIVE',0,0)
                """);
        jdbc.update("""
                insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                listing_status,moderation_status,social_caption,created_at,updated_at,version)
                select md5('preview-plan-item-'||g)::uuid,md5('preview-plan-merchant-'||((g-1)%1000+1))::uuid,
                'PRODUCT','STANDARD',md5('preview-plan-category')::uuid,'Preview item '||g,'preview-plan-item-'||g,
                'ACTIVE','APPROVED',repeat('x',3000),now(),now(),0 from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,
                media_status,upload_expires_at,verified_at,created_at)
                select md5('preview-plan-media-'||g)::uuid,'S3','preview-plan-media-'||g,'CATALOG_IMAGE','image/jpeg',128,
                'READY',now()+interval '1 hour',now(),now() from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary)
                select md5('preview-plan-item-'||((g-1)/10+1))::uuid,md5('preview-plan-media-'||g)::uuid,
                'IMAGE',(g-1)%10,(g-1)%10=0 from generate_series(1,200000) g
                """);
        for (String table : List.of("catalog_items","merchant_profiles","catalog_item_media","media_assets"))
            jdbc.execute("analyze "+table);
        assertThat(jdbc.queryForObject("select count(*) from catalog_items",Long.class)).isGreaterThanOrEqualTo(20000);
        assertThat(jdbc.queryForObject("select count(*) from media_assets",Long.class)).isGreaterThanOrEqualTo(200000);
        var ids=jdbc.queryForList("select md5('preview-plan-item-'||g)::uuid from generate_series(1,40) g",UUID.class);
        var output=Path.of(System.getProperty("covie.test.social-preview-plan-output","target/social-preview-plans"));
        Files.createDirectories(output);
        var evidence=new ArrayList<String>();
        try {
            for (int count : List.of(1,40)) {
                org.mockito.Mockito.clearInvocations(named);
                SqlCapture.SQL.get().clear();
                long started=System.nanoTime();
                var response=mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",
                        ids.subList(0,count).stream().map(UUID::toString).toArray(String[]::new)))
                        .andExpect(status().isOk()).andReturn().getResponse();
                double elapsedMs=(System.nanoTime()-started)/1_000_000.0;
                var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
                assertThat(body.size()).isEqualTo(count);
                var mediaIds=new ArrayList<UUID>();
                for (var item : body) {
                    assertThat(item.path("socialCaption").asText()).hasSize(3000);
                    assertThat(item.path("media").size()).isEqualTo(6);
                    for (var media : item.path("media")) mediaIds.add(UUID.fromString(media.path("id").asText()));
                }
                assertThat(mediaIds).hasSize(count*6).doesNotHaveDuplicates();
                var calls=org.mockito.Mockito.mockingDetails(named).getInvocations().stream()
                        .filter(invocation -> invocation.getMethod().getName().equals("query")).toList();
                assertThat(calls).hasSize(1);
                assertThat(SqlCapture.SQL.get()).hasSize(1);
                String batchSql=(String) calls.getFirst().getArgument(0);
                SqlParameterSource parameters=calls.getFirst().getArgument(1);
                String assetSql=SqlCapture.SQL.get().getFirst();
                assertThat(assetSql).contains(" from media_assets ");
                assertThat(assetSql.indexOf("media_status=?")).isLessThan(assetSql.indexOf("upload_purpose=?"));
                assertThat(assetSql.chars().filter(character -> character=='?').count()).isEqualTo(mediaIds.size()+2);
                String batchPlan=named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+batchSql,parameters,String.class);
                var assetParameters=new ArrayList<Object>(mediaIds);
                assetParameters.add("READY"); assetParameters.add("CATALOG_IMAGE");
                String assetPlan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+assetSql,
                        String.class,assetParameters.toArray());
                assertPlan(batchPlan,count*6);
                assertPlan(assetPlan,count*6);
                Files.writeString(output.resolve("batch-"+count+".sql"),batchSql);
                Files.writeString(output.resolve("assets-"+count+".sql"),assetSql);
                Files.writeString(output.resolve("batch-"+count+".json"),batchPlan);
                Files.writeString(output.resolve("assets-"+count+".json"),assetPlan);
                evidence.add("items="+count+" responseBytes="+response.getContentAsByteArray().length
                        +" requestMs="+elapsedMs+" namedQueries=1 hibernateSelects=1 returnedMedia="+mediaIds.size());
            }
            Files.write(output.resolve("measurements.txt"),evidence);
        } finally { SqlCapture.SQL.remove(); }
    }

    private void assertPlan(String text,int expectedRows) throws Exception {
        var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(text).get(0);
        assertThat(root.path("Plan").path("Actual Rows").asInt()).isEqualTo(expectedRows);
        assertThat(root.path("Execution Time").asDouble()).isGreaterThanOrEqualTo(0);
        assertIndexedLargeRelations(root.path("Plan"));
    }

    private void assertIndexedLargeRelations(com.fasterxml.jackson.databind.JsonNode node) {
        if (List.of("catalog_items","catalog_item_media","media_assets").contains(node.path("Relation Name").asText()))
            assertThat(node.path("Node Type").asText()).isNotEqualTo("Seq Scan");
        for (var child : node.path("Plans")) assertIndexedLargeRelations(child);
    }
}
