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
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;

@Tag("performance")
@EnabledIfSystemProperty(named="covie.test.seller-reason-plan",matches="true")
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.caption-authoring-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false"})
@Transactional
class SellerModerationReasonQueryPlanIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;

    @Test void capturesActualSellerReasonQueriesAtExistingCaseFixtureScale() throws Exception {
        jdbc.update("""
                insert into users(id,display_name,account_status,created_at,updated_at,version)
                select md5('seller-reason-plan-'||role)::uuid,'Reason plan '||role,'ACTIVE',now(),now(),0
                from (values ('owner'),('admin')) roles(role)
                """);
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (md5('seller-reason-plan-admin-role')::uuid,md5('seller-reason-plan-admin')::uuid,'ADMIN',now())");
        jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                values(md5('seller-reason-plan-shop')::uuid,'Reason shop','seller-reason-plan-shop','ACTIVE','APPROVED',now(),now(),0)
                """);
        jdbc.update("""
                insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at)
                values(md5('seller-reason-plan-member')::uuid,md5('seller-reason-plan-shop')::uuid,md5('seller-reason-plan-owner')::uuid,'OWNER','ACTIVE',now(),now())
                """);
        jdbc.update("""
                insert into categories(id,name,slug,catalog_type,status,sort_order,version)
                values(md5('seller-reason-plan-category')::uuid,'Reason category','seller-reason-plan-category','PRODUCT','ACTIVE',0,0)
                """);
        jdbc.update("""
                insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                listing_status,moderation_status,created_at,updated_at,version)
                select md5('seller-reason-plan-item-'||g)::uuid,md5('seller-reason-plan-shop')::uuid,'PRODUCT','STANDARD',
                md5('seller-reason-plan-category')::uuid,'Reason item '||g,'seller-reason-plan-item-'||g,'ACTIVE',
                case g when 1 then 'HIDDEN' when 2 then 'REJECTED' else 'APPROVED' end,now(),now(),0
                from generate_series(1,20000) g
                """);
        jdbc.update("""
                insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at)
                select md5('seller-reason-plan-case-'||g)::uuid,case when g=2 then 'PRE_APPROVAL' else 'USER_REPORT' end,
                'CATALOG_ITEM',md5('seller-reason-plan-item-'||((g-1)%20000+1))::uuid,'OPEN_REASON_PRIVATE',
                case when g=2 then 'RESOLVED' else 'CLOSED' end,now()-g*interval '1 second',now()
                from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at)
                select md5('seller-reason-plan-extra-'||g)::uuid,'SYSTEM_FLAG','CATALOG_ITEM',
                md5('seller-reason-plan-item-1')::uuid,'OPEN_REASON_PRIVATE',case when g=50 then 'RESOLVED' else 'CLOSED' end,
                now()-g*interval '1 second',now() from generate_series(1,50) g
                """);
        jdbc.update("""
                insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,note,metadata,created_at)
                select md5('seller-reason-plan-action-'||g)::uuid,md5('seller-reason-plan-case-'||g)::uuid,
                case when g=2 then 'REJECT' else 'CLOSE' end,md5('seller-reason-plan-admin')::uuid,
                case when g=2 then 'CURRENT_REJECT' else 'HISTORICAL_CLOSE' end,'ADMIN_NOTE_PRIVATE','{}'::jsonb,
                now()-g*interval '1 second' from generate_series(1,200000) g
                """);
        jdbc.update("""
                insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,note,metadata,created_at)
                select md5('seller-reason-plan-extra-action-'||g)::uuid,md5('seller-reason-plan-extra-'||g)::uuid,
                case when g=50 then 'HIDE' else 'CLOSE' end,md5('seller-reason-plan-admin')::uuid,
                case when g=50 then 'CURRENT_HIDE' else 'HISTORICAL_CLOSE' end,'ADMIN_NOTE_PRIVATE','{}'::jsonb,
                now()-g*interval '1 second' from generate_series(1,50) g
                """);
        for (String table:List.of("catalog_items","moderation_cases","moderation_actions")) jdbc.execute("analyze "+table);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isEqualTo(200050L);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isEqualTo(200050L);
        var before=jdbc.queryForList("select to_jsonb(original)::text from catalog_items original order by id limit 3",String.class);
        UUID actor=jdbc.queryForObject("select md5('seller-reason-plan-owner')::uuid",UUID.class);
        UUID merchant=jdbc.queryForObject("select md5('seller-reason-plan-shop')::uuid",UUID.class);
        String token="Bearer "+issuer.issue(actor,new DeviceMetadata("Test","Seller reason plans")).accessToken().value();
        var output=Path.of(System.getProperty("covie.test.seller-reason-plan-output","target/seller-reason-plans"));
        Files.createDirectories(output);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var measurements=new ArrayList<String>();
        for (int position=1;position<=3;position++) {
            UUID item=jdbc.queryForObject("select md5('seller-reason-plan-item-'||?)::uuid",UUID.class,Integer.toString(position));
            org.mockito.Mockito.clearInvocations(jdbc);
            long started=System.nanoTime();
            var response=mvc.perform(get("/api/v1/merchants/"+merchant+"/catalog-items/"+item).header("Authorization",token))
                    .andExpect(status().isOk()).andReturn().getResponse();
            double requestMs=(System.nanoTime()-started)/1_000_000.0;
            var body=mapper.readTree(response.getContentAsString());
            assertThat(response.getContentAsString()).doesNotContain("ADMIN_NOTE_PRIVATE","OPEN_REASON_PRIVATE","HISTORICAL_CLOSE");
            var calls=org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream().filter(call -> {
                Object[] raw=call.getRawArguments();
                return call.getMethod().getName().equals("query") && raw.length==3
                        && raw[0] instanceof String sql && sql.stripLeading().startsWith("select decision.reason_code")
                        && raw[1] instanceof ResultSetExtractor<?> && raw[2] instanceof Object[];
            }).toList();
            assertThat(calls).as("one conditional reason lookup, not all authentication/detail queries").hasSize(position==3?0:1);
            if (position==3) assertThat(body.has("moderationReasonCode")).isFalse();
            else {
                assertThat(body.path("moderationReasonCode").asText()).isEqualTo(position==1?"CURRENT_HIDE":"CURRENT_REJECT");
                String sql=calls.getFirst().getArgument(0);
                Object[] parameters=(Object[])calls.getFirst().getRawArguments()[2];
                assertThat(parameters).containsExactly(item,position==1?"HIDE":"REJECT");
                String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,String.class,parameters);
                Files.writeString(output.resolve("item-"+position+".sql"),sql);
                Files.writeString(output.resolve("item-"+position+".json"),plan);
                var root=mapper.readTree(plan).get(0);
                assertScopedAccess(root.path("Plan"));
                measurements.add("item="+position+" reasonQueries=1 executionMs="+root.path("Execution Time").asDouble()
                        +" planRows="+root.path("Plan").path("Actual Rows").asInt());
            }
            measurements.add("item="+position+" reasonQueries="+calls.size()+" requestMs="+requestMs+" responseBytes="+response.getContentAsByteArray().length);
        }
        assertThat(jdbc.queryForList("select to_jsonb(original)::text from catalog_items original order by id limit 3",String.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isEqualTo(200050L);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isEqualTo(200050L);
        Files.write(output.resolve("measurements.txt"),measurements);
    }

    private void assertScopedAccess(com.fasterxml.jackson.databind.JsonNode node) {
        if (List.of("moderation_cases","moderation_actions").contains(node.path("Relation Name").asText()))
            assertThat(node.path("Node Type").asText()).as("one item's reason must not scan all other items' histories").isNotEqualTo("Seq Scan");
        for (var child:node.path("Plans")) assertScopedAccess(child);
    }
}
