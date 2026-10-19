package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.caption-authoring-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false","spring.datasource.hikari.maximum-pool-size=6"})
class SocialEditMembershipRevocationIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;

    @Test void committedRevocationCannotBeFollowedByWaitingCaptionAndMediaWrite() throws Exception {
        UUID owner=UUID.randomUUID(),manager=UUID.randomUUID(),merchant=UUID.randomUUID(),membership=UUID.randomUUID();
        UUID category=UUID.randomUUID(),item=UUID.randomUUID(),oldMedia=UUID.randomUUID(),newMedia=UUID.randomUUID();
        users.saveAllAndFlush(List.of(UserEntity.active(owner,"Revocation race owner",Instant.now()),
                UserEntity.active(manager,"Revocation race manager",Instant.now())));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Revocation race shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"revocation-race-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now()),(?,?,?,'MANAGER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner,membership,merchant,manager);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Revocation race category',?,'PRODUCT','ACTIVE')",category,"revocation-race-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Revocation race item',?,'Preserved description','Preserved caption','DRAFT','NOT_REQUIRED',now(),now())",item,merchant,category,"revocation-race-"+item);
        for (UUID media:List.of(oldMedia,newMedia)) jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'READY',now()+interval '1 hour',now(),now())",media,manager,"revocation-race-"+media);
        jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',0,true)",item,oldMedia);
        var beforeItem=jdbc.queryForMap("select * from catalog_items where id=?",item);
        var beforeGallery=jdbc.queryForList("select * from catalog_item_media where catalog_item_id=?",item);
        var beforeAssets=jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,manager);
        String ownerToken=token(owner),managerToken=token(manager);
        String editRoute="/api/v1/merchants/"+merchant+"/catalog-items/"+item;
        try (var membershipBlocker=jdbc.getDataSource().getConnection();var itemBlocker=jdbc.getDataSource().getConnection();
                var workers=Executors.newFixedThreadPool(2)) {
            membershipBlocker.setAutoCommit(false);
            itemBlocker.setAutoCommit(false);
            try (var lock=membershipBlocker.prepareStatement("select id from merchant_memberships where id=? for update")) {
                lock.setObject(1,membership);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            try (var lock=itemBlocker.prepareStatement("select id from catalog_items where id=? for no key update")) {
                lock.setObject(1,item);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var revocation=workers.submit(() -> mvc.perform(delete("/api/v1/merchants/"+merchant+"/members/"+membership)
                    .header("Authorization",ownerToken)).andReturn().getResponse());
            java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse> edit;
            try {
                awaitWaiters(1);
                edit=workers.submit(() -> mvc.perform(patch(editRoute).header("Authorization",managerToken)
                        .header("If-Match","\"v0\"").contentType("application/json")
                        .content("{\"socialCaption\":\"Must not commit after revocation\",\"mediaIds\":[\""+newMedia+"\"]}"))
                        .andReturn().getResponse());
                awaitWaiters(2);
                membershipBlocker.rollback();
                var revoked=revocation.get(15,TimeUnit.SECONDS);
                assertThat(revoked.getStatus()).as(revoked.getContentAsString()).isEqualTo(204);
                assertThat(jdbc.queryForObject("select membership_status from merchant_memberships where id=?",String.class,membership)).isEqualTo("REVOKED");
                assertThat(jdbc.queryForMap("select * from catalog_items where id=?",item)).isEqualTo(beforeItem);
            } finally {
                membershipBlocker.rollback();
                itemBlocker.rollback();
            }
            var result=edit.get(15,TimeUnit.SECONDS);
            System.out.println("Committed membership revocation=204 waiting Social PATCH="+result.getStatus());
            assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(404);
            assertThat(result.getContentAsString()).contains("RESOURCE_NOT_FOUND");
        }
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",item)).isEqualTo(beforeItem);
        assertThat(jdbc.queryForList("select * from catalog_item_media where catalog_item_id=?",item)).isEqualTo(beforeGallery);
        assertThat(jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,manager)).isEqualTo(beforeAssets);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,item)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MERCHANT_MEMBERSHIP_REVOKED'",Long.class,membership)).isEqualTo(1L);
        mvc.perform(patch(editRoute).header("Authorization",managerToken).header("If-Match","\"v0\"")
                .contentType("application/json").content("{\"socialCaption\":\"Same JWT retry\"}"))
                .andExpect(status().isNotFound());
    }

    private String token(UUID user) {
        return "Bearer "+issuer.issue(user,new DeviceMetadata("Test","Revocation race")).accessToken().value();
    }

    private void awaitWaiters(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,query,wait_event from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and (query ilike '%update merchant_memberships%' or query ilike '%update catalog_items%' or (query ilike '%merchant_memberships%' and query ilike '%for share%'))");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("real membership revocation and Social edit database waits").hasSize(expected);
        System.out.println("Membership revocation/edit waiters="+expected+" queries="+waiting.stream().map(row -> row.get("query")).toList());
    }
}
