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
@EnabledIfSystemProperty(named="covie.test.admin-case-plan",matches="true")
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.admin-reads-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false"})
@Transactional
class AdminCatalogCaseQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    @Test void capturesExecutedItemScopedCasePlansAtTwoHundredThousandCases() throws Exception {
        jdbc.update("""
                insert into users(id,display_name,account_status,created_at,updated_at,version)
                values(md5('admin-case-plan-user')::uuid,'Case plan admin','ACTIVE',now(),now(),0)
                """);
        jdbc.update("""
                insert into system_role_assignments(id,user_id,role,created_at)
                values(md5('admin-case-plan-role')::uuid,md5('admin-case-plan-user')::uuid,'ADMIN',now())
                """);
        jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                values(md5('admin-case-plan-shop')::uuid,'Case shop','admin-case-plan-shop','ACTIVE','APPROVED',now(),now(),0)
                """);
        jdbc.update("""
                insert into categories(id,name,slug,catalog_type,status,sort_order,version)
                values(md5('admin-case-plan-category')::uuid,'Case category','admin-case-plan-category','PRODUCT','ACTIVE',0,0)
                """);
        jdbc.update("""
                insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                listing_status,moderation_status,created_at,updated_at,version)
                select md5('admin-case-plan-item-'||g)::uuid,md5('admin-case-plan-shop')::uuid,'PRODUCT','STANDARD',
                md5('admin-case-plan-category')::uuid,'Case item '||g,'admin-case-plan-item-'||g,
                'ACTIVE','APPROVED',now(),now(),0 from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at)
                select md5('admin-case-plan-case-'||g)::uuid,'USER_REPORT','CATALOG_ITEM',
                md5('admin-case-plan-item-'||((g-1)%20000+1))::uuid,'SPAM','RESOLVED',
                now()-g*interval '1 second',now() from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at)
                select md5('admin-case-plan-extra-'||g)::uuid,'SYSTEM_FLAG','CATALOG_ITEM',
                md5('admin-case-plan-item-1')::uuid,'SPAM','RESOLVED',
                now()-g*interval '1 second',now() from generate_series(1,50) g
                """);
        for (String table : List.of("catalog_items","moderation_cases")) jdbc.execute("analyze "+table);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isGreaterThanOrEqualTo(200050);
        UUID actor=jdbc.queryForObject("select md5('admin-case-plan-user')::uuid",UUID.class);
        UUID itemId=jdbc.queryForObject("select md5('admin-case-plan-item-1')::uuid",UUID.class);
        String token="Bearer "+issuer.issue(actor,new DeviceMetadata("Test","Admin case plans")).accessToken().value();
        var output=Path.of(System.getProperty("covie.test.admin-case-plan-output","target/admin-case-plans"));
        Files.createDirectories(output);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var measurements=new ArrayList<String>();
        var plans=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var seen=new ArrayList<String>();
        String cursor=null;
        for (int page=0;page<2;page++) {
            org.mockito.Mockito.clearInvocations(named);
            var request=get("/api/v1/admin/catalog-items/"+itemId+"/moderation-cases").header("Authorization",token);
            if (cursor!=null) request.param("cursor",cursor);
            long started=System.nanoTime();
            var response=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
            double elapsedMs=(System.nanoTime()-started)/1_000_000.0;
            var body=mapper.readTree(response.getContentAsString());
            assertThat(body.path("total").asLong()).isEqualTo(60);
            assertThat(body.path("items")).hasSize(page==0?50:10);
            assertThat(body.path("hasMore").asBoolean()).isEqualTo(page==0);
            for (var entry : body.path("items")) {
                assertThat(entry.path("targetId").asText()).isEqualTo(itemId.toString());
                seen.add(entry.path("id").asText());
            }
            cursor=page==0?body.path("nextCursor").asText():null;
            if (page==0) assertThat(cursor).isNotBlank();
            var calls=org.mockito.Mockito.mockingDetails(named).getInvocations().stream()
                    .filter(call -> call.getArguments().length >= 3 && call.getArgument(0) instanceof String sql && sql.contains("moderation_cases")
                            && !sql.startsWith("EXPLAIN") && (sql.startsWith("select count(*)")
                            ? call.getMethod().getName().equals("queryForObject") && call.getArgument(2) instanceof Class<?>
                            : call.getMethod().getName().equals("query"))).toList();
            assertThat(calls).hasSize(2);
            for (int query=0;query<calls.size();query++) {
                String sql=calls.get(query).getArgument(0);
                SqlParameterSource parameters=calls.get(query).getArgument(1);
                String plan=named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,parameters,String.class);
                String label="page-"+page+"-query-"+query;
                Files.writeString(output.resolve(label+".sql"),sql);
                Files.writeString(output.resolve(label+".json"),plan);
                var root=mapper.readTree(plan).get(0);
                plans.add(root.path("Plan"));
                measurements.add(label+" executionMs="+root.path("Execution Time").asDouble());
            }
            measurements.add("page="+page+" moderationQueries=2 returned="+body.path("items").size()
                    +" requestMs="+elapsedMs+" responseBytes="+response.getContentAsByteArray().length);
        }
        assertThat(seen).hasSize(60).doesNotHaveDuplicates();
        Files.write(output.resolve("measurements.txt"),measurements);
        for (var plan : plans) assertItemScopedAccess(plan);
    }

    private void assertItemScopedAccess(com.fasterxml.jackson.databind.JsonNode node) {
        if ("moderation_cases".equals(node.path("Relation Name").asText()))
            assertThat(node.path("Node Type").asText()).as("one item's case history must not scan all other items' cases").isNotEqualTo("Seq Scan");
        for (var child : node.path("Plans")) assertItemScopedAccess(child);
    }
}
