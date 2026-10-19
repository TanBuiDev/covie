package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SocialCreateMembershipRevocationIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;

    @Test void committedRevocationCannotBeFollowedByWaitingCaptionCreate() throws Exception {
        UUID owner=UUID.randomUUID(),manager=UUID.randomUUID(),merchant=UUID.randomUUID(),membership=UUID.randomUUID();
        UUID category=UUID.randomUUID(),location=UUID.randomUUID(),media=UUID.randomUUID();
        users.saveAllAndFlush(List.of(UserEntity.active(owner,"Create race owner",Instant.now()),
                UserEntity.active(manager,"Create race manager",Instant.now())));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Create race shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"create-revocation-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now()),(?,?,?,'MANAGER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner,membership,merchant,manager);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Create race category',?,'PRODUCT','ACTIVE')",category,"create-revocation-"+category);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Create race counter','Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'READY',now()+interval '1 hour',now(),now())",media,manager,"create-revocation-"+media);
        var beforeMerchant=jdbc.queryForMap("select * from merchant_profiles where id=?",merchant);
        var beforeLocation=jdbc.queryForMap("select * from merchant_locations where id=?",location);
        var beforeMedia=jdbc.queryForMap("select * from media_assets where id=?",media);
        String ownerToken=token(owner),managerToken=token(manager),key=UUID.randomUUID().toString();
        String route="/api/v1/merchants/"+merchant+"/catalog-items";
        String body="{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\""+category
                +"\",\"name\":\"Waiting caption create\",\"socialCaption\":\"Create race caption\",\"mediaIds\":[\""+media
                +"\"],\"locationOffers\":[{\"merchantLocationId\":\""+location
                +"\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
        try (var membershipBlocker=jdbc.getDataSource().getConnection();var merchantBlocker=jdbc.getDataSource().getConnection();
                var workers=Executors.newFixedThreadPool(2)) {
            membershipBlocker.setAutoCommit(false);
            merchantBlocker.setAutoCommit(false);
            try (var lock=membershipBlocker.prepareStatement("select id from merchant_memberships where id=? for update")) {
                lock.setObject(1,membership);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            try (var lock=merchantBlocker.prepareStatement("select id from merchant_profiles where id=? for no key update")) {
                lock.setObject(1,merchant);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var revocation=workers.submit(() -> mvc.perform(delete("/api/v1/merchants/"+merchant+"/members/"+membership)
                    .header("Authorization",ownerToken)).andReturn().getResponse());
            java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse> creation;
            try {
                awaitWaiters(1);
                creation=workers.submit(() -> mvc.perform(post(route).header("Authorization",managerToken)
                        .header("Idempotency-Key",key).contentType("application/json").content(body)).andReturn().getResponse());
                awaitWaiters(2);
                membershipBlocker.rollback();
                var revoked=revocation.get(15,TimeUnit.SECONDS);
                assertThat(revoked.getStatus()).as(revoked.getContentAsString()).isEqualTo(204);
                assertThat(jdbc.queryForObject("select membership_status from merchant_memberships where id=?",String.class,membership)).isEqualTo("REVOKED");
                assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant)).isZero();
            } finally {
                membershipBlocker.rollback();
                merchantBlocker.rollback();
            }
            var result=creation.get(15,TimeUnit.SECONDS);
            System.out.println("Committed membership revocation=204 waiting Social Create="+result.getStatus()
                    +" persistedItems="+jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant));
            assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(404);
            assertThat(result.getContentAsString()).contains("RESOURCE_NOT_FOUND");
        }
        assertThat(jdbc.queryForMap("select * from merchant_profiles where id=?",merchant)).isEqualTo(beforeMerchant);
        assertThat(jdbc.queryForMap("select * from merchant_locations where id=?",location)).isEqualTo(beforeLocation);
        assertThat(jdbc.queryForMap("select * from media_assets where id=?",media)).isEqualTo(beforeMedia);
        assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from location_variants where merchant_location_id=?",Long.class,location)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from catalog_item_media where media_asset_id=?",Long.class,media)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where operation=?",Long.class,"CATALOG_ITEM_CREATE:"+merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='CATALOG_ITEM_CREATED' and payload->>'merchantId'=?",Long.class,merchant.toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MERCHANT_MEMBERSHIP_REVOKED'",Long.class,membership)).isEqualTo(1L);
        mvc.perform(post(route).header("Authorization",managerToken).header("Idempotency-Key",key)
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
    }

    @Test void committedMerchantSuspensionCannotBeFollowedByWaitingCaptionCreate() throws Exception {
        UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),merchant=UUID.randomUUID(),membership=UUID.randomUUID();
        UUID category=UUID.randomUUID(),location=UUID.randomUUID(),media=UUID.randomUUID();
        users.saveAllAndFlush(List.of(UserEntity.active(owner,"Suspension race owner",Instant.now()),
                UserEntity.active(admin,"Suspension race Admin",Instant.now())));
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Suspension race shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"create-suspension-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",membership,merchant,owner);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Suspension race category',?,'PRODUCT','ACTIVE')",category,"create-suspension-"+category);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Suspension counter','Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'READY',now()+interval '1 hour',now(),now())",media,owner,"create-suspension-"+media);
        var beforeMerchant=jdbc.queryForMap("select * from merchant_profiles where id=?",merchant);
        var beforeMembership=jdbc.queryForMap("select * from merchant_memberships where id=?",membership);
        var beforeLocation=jdbc.queryForMap("select * from merchant_locations where id=?",location);
        var beforeMedia=jdbc.queryForMap("select * from media_assets where id=?",media);
        String ownerToken=token(owner),adminToken=token(admin),key=UUID.randomUUID().toString();
        String route="/api/v1/merchants/"+merchant+"/catalog-items";
        String body="{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\""+category
                +"\",\"name\":\"Waiting merchant caption create\",\"socialCaption\":\"Suspension race caption\",\"mediaIds\":[\""+media
                +"\"],\"locationOffers\":[{\"merchantLocationId\":\""+location
                +"\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
        try (var blocker=jdbc.getDataSource().getConnection();var workers=Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock=blocker.prepareStatement("select id from merchant_profiles where id=? for no key update")) {
                lock.setObject(1,merchant);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var suspension=workers.submit(() -> mvc.perform(post("/api/v1/admin/merchants/"+merchant+"/suspend")
                    .header("Authorization",adminToken).header("Idempotency-Key",UUID.randomUUID().toString())
                    .contentType("application/json").content("{\"reasonCode\":\"SPAM\"}")).andReturn().getResponse());
            java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse> creation;
            try {
                awaitMerchantWaiters(1);
                creation=workers.submit(() -> mvc.perform(post(route).header("Authorization",ownerToken)
                        .header("Idempotency-Key",key).contentType("application/json").content(body)).andReturn().getResponse());
                awaitMerchantWaiters(2);
            } finally { blocker.rollback(); }
            var suspended=suspension.get(15,TimeUnit.SECONDS);
            assertThat(suspended.getStatus()).as(suspended.getContentAsString()).isEqualTo(200);
            var created=creation.get(15,TimeUnit.SECONDS);
            System.out.println("Committed Admin suspension="+suspended.getStatus()+" waiting Social Create="+created.getStatus());
            assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(422);
            assertThat(created.getContentAsString()).contains("MERCHANT_ACTION_NOT_ALLOWED");
        }
        var afterMerchant=jdbc.queryForMap("select * from merchant_profiles where id=?",merchant);
        var expectedMerchant=new java.util.LinkedHashMap<>(beforeMerchant);
        expectedMerchant.put("merchant_status","SUSPENDED");
        expectedMerchant.put("status_before_suspension","ACTIVE");
        expectedMerchant.put("version",1L);
        expectedMerchant.put("updated_at",afterMerchant.get("updated_at"));
        assertThat(afterMerchant).isEqualTo(expectedMerchant);
        assertThat(jdbc.queryForMap("select * from merchant_memberships where id=?",membership)).isEqualTo(beforeMembership);
        assertThat(jdbc.queryForMap("select * from merchant_locations where id=?",location)).isEqualTo(beforeLocation);
        assertThat(jdbc.queryForMap("select * from media_assets where id=?",media)).isEqualTo(beforeMedia);
        assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from location_variants where merchant_location_id=?",Long.class,location)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from catalog_item_media where media_asset_id=?",Long.class,media)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where operation=?",Long.class,"CATALOG_ITEM_CREATE:"+merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='CATALOG_ITEM_CREATED' and payload->>'merchantId'=?",Long.class,merchant.toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_MERCHANT_SUSPENDED'",Long.class,merchant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='ADMIN_MERCHANT_SUSPENDED'",Long.class,merchant)).isEqualTo(1L);
        mvc.perform(post(route).header("Authorization",ownerToken).header("Idempotency-Key",key)
                .contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity());
    }

    private void awaitMerchantWaiters(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,query,wait_event from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%merchant_profiles%' and query ilike '%for%update%'");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("actual Admin suspension and Social Create merchant FOR UPDATE waits").hasSize(expected);
        System.out.println("Merchant suspension/Create waiters="+expected+" queries="+waiting.stream().map(row -> row.get("query")).toList());
    }

    private String token(UUID user) {
        return "Bearer "+issuer.issue(user,new DeviceMetadata("Test","Create revocation race")).accessToken().value();
    }

    private void awaitWaiters(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,query,wait_event from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and (query ilike '%update merchant_memberships%' or (query ilike '%merchant_profiles%' and query ilike '%for%update%') or (query ilike '%merchant_memberships%' and query ilike '%for share%'))");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("actual membership revocation and Social Create database waits").hasSize(expected);
        System.out.println("Membership revocation/Create waiters="+expected+" queries="+waiting.stream().map(row -> row.get("query")).toList());
    }
}
