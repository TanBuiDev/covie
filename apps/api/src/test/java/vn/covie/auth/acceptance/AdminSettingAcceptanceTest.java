package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

@AutoConfigureMockMvc
class AdminSettingAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired vn.covie.settings.application.SystemSettingResolver resolver;
    @Autowired vn.covie.needs.application.NeedPolicyProvider needPolicy;
    @Autowired vn.covie.merchants.persistence.MerchantProfileRepository merchants;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    vn.covie.settings.persistence.SystemSettingRepository settings;

    @Test void concurrentCreatesReturnTheSpecifiedConflictWithoutDuplicateSideEffects() throws Exception {
        String bearer=adminBearer(), key="bulk.max_items", path="/api/v1/admin/settings/"+key;
        var reads=new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(invocation->{
            assertThat(jdbc.queryForObject("select count(*) from system_settings where setting_key=? and scope_type='GLOBAL'",Long.class,key)).isZero();
            reads.await(10,java.util.concurrent.TimeUnit.SECONDS);
            return java.util.Optional.empty();
        }).when(settings).findBySettingKeyAndScopeType(key,vn.covie.settings.model.SettingScopeType.GLOBAL);
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> command=()->mvc.perform(
                    patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"scopeType\":\"GLOBAL\",\"value\":120}")).andReturn();
            var first=workers.submit(command); var second=workers.submit(command);
            var results=java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().map(r->r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201,409);
            var rejected=results.stream().filter(r->r.getResponse().getStatus()==409).findFirst().orElseThrow();
            assertThat(rejected.getResponse().getContentAsString()).contains("SYSTEM_SETTING_OVERRIDE_ALREADY_EXISTS");
            UUID id=jdbc.queryForObject("select id from system_settings where setting_key=? and scope_type='GLOBAL'",UUID.class,key);
            assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(1);
        } finally { workers.shutdownNow(); }
    }

    @Test void concurrentUpdatesWithTheSameVersionCommitOnlyOneChange() throws Exception {
        String bearer=adminBearer(),path="/api/v1/admin/settings/bulk.max_items";
        var created=mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":120}")).andExpect(status().isCreated()).andReturn();
        String etag=created.getResponse().getHeader("ETag");
        var start=new java.util.concurrent.CyclicBarrier(2);
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=workers.submit(()->updateAtBarrier(path,bearer,etag,121,start));
            var second=workers.submit(()->updateAtBarrier(path,bearer,etag,122,start));
            var results=java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().map(r->r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(200,409);
            assertThat(results.stream().filter(r->r.getResponse().getStatus()==409).findFirst().orElseThrow().getResponse().getContentAsString())
                    .contains("RESOURCE_VERSION_CONFLICT");
            UUID id=jdbc.queryForObject("select id from system_settings where setting_key='bulk.max_items' and scope_type='GLOBAL'",UUID.class);
            assertThat(jdbc.queryForObject("select version from system_settings where id=?",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?",Long.class,id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(2);
        } finally { workers.shutdownNow(); }
    }
    private org.springframework.test.web.servlet.MvcResult updateAtBarrier(String path,String bearer,String etag,int value,
            java.util.concurrent.CyclicBarrier start) throws Exception {
        start.await(10,java.util.concurrent.TimeUnit.SECONDS);
        return mvc.perform(patch(path).header("Authorization",bearer).header("If-Match",etag).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":"+value+"}")).andReturn();
    }

    @Test void settingUpdatesEnforceVersionAndPersistHistoryAndEventsAtomically() throws Exception {
        var admin=activeUser("Settings operator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String path="/api/v1/admin/settings/bulk.max_items";
        var created=mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":120}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.value").value(120)).andReturn();
        String etag=created.getResponse().getHeader("ETag");
        UUID id=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":121}"))
                .andExpect(status().is(428)).andExpect(jsonPath("$.error.code").value("IF_MATCH_REQUIRED"));
        mvc.perform(patch(path).header("Authorization",bearer).header("If-Match",etag).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":121}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value(121));
        mvc.perform(patch(path).header("Authorization",bearer).header("If-Match",etag).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":122}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveValue").value(121));
        mvc.perform(get(path+"/history").header("Authorization",bearer).param("scopeType","GLOBAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
        assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='SYSTEM_SETTING_CHANGED'",Long.class,id)).isEqualTo(2);
    }
    @Test void invalidValuesScopesAndNonAdminWritesLeaveNoSettingsOrHistory() throws Exception {
        String admin=adminBearer();
        String ordinary="Bearer "+issuer.issue(activeUser("Ordinary settings user").getId(),device()).accessToken().value();
        String path="/api/v1/admin/settings/bulk.max_items";
        mvc.perform(patch(path).header("Authorization",ordinary).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":120}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
        for(String value:new String[]{"0","501","\"120\"","true"})
            mvc.perform(patch(path).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scopeType\":\"GLOBAL\",\"value\":"+value+"}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SYSTEM_SETTING_VALUE_INVALID"));
        mvc.perform(patch(path).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"MERCHANT\",\"scopeId\":\""+UUID.randomUUID()+"\",\"value\":120}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SYSTEM_SETTING_SCOPE_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select count(*) from system_settings where setting_key='bulk.max_items'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from system_setting_history",Long.class)).isZero();
    }

    @Test void scopedOverridesFollowMerchantCategoryAreaGlobalOrderInApiAndBusinessResolver() throws Exception {
        String bearer=adminBearer(); UUID merchant=UUID.randomUUID();
        UUID category=UUID.randomUUID(),area=UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Settings category","settings-"+category);
        java.sql.Timestamp now=java.sql.Timestamp.from(Instant.now());
        jdbc.update("insert into location_areas(id,area_type,name,slug,status,sort_order,created_at,updated_at,version) values (?,'COMMUNITY',?,?,'ACTIVE',0,?,?,0)",area,"Settings area","settings-"+area,now,now);
        merchants.saveAndFlush(vn.covie.merchants.model.MerchantProfileEntity.create(merchant,"Settings shop","settings-"+merchant,Instant.now()));
        String key="need_request.max_response_items_per_location",path="/api/v1/admin/settings/"+key;
        mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":4}"))
                .andExpect(status().isCreated());
        mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"MERCHANT\",\"scopeId\":\""+merchant+"\",\"value\":2}"))
                .andExpect(status().isCreated());
        mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"CATEGORY\",\"scopeId\":\""+category+"\",\"value\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(patch(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"LOCATION_AREA\",\"scopeId\":\""+area+"\",\"value\":5}"))
                .andExpect(status().isCreated());
        mvc.perform(get(path).header("Authorization",bearer).param("merchantId",merchant.toString())
                .param("categoryId",category.toString()).param("locationAreaId",area.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveValue").value(2))
                .andExpect(jsonPath("$.effectiveSource.scopeType").value("MERCHANT"));
        mvc.perform(get(path).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveValue").value(4));
        assertThat(resolver.integer(key,new vn.covie.settings.application.SystemSettingContext(merchant,null,null))).isEqualTo(2);
        assertThat(resolver.integer(key,vn.covie.settings.application.SystemSettingContext.globalOnly())).isEqualTo(4);
        mvc.perform(get(path).header("Authorization",bearer).param("categoryId",category.toString()).param("locationAreaId",area.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveValue").value(3));
        mvc.perform(get(path).header("Authorization",bearer).param("locationAreaId",area.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveValue").value(5));
        assertThat(resolver.integer(key,new vn.covie.settings.application.SystemSettingContext(merchant,category,area))).isEqualTo(2);
        assertThat(resolver.integer(key,new vn.covie.settings.application.SystemSettingContext(null,category,area))).isEqualTo(3);
        assertThat(resolver.integer(key,new vn.covie.settings.application.SystemSettingContext(null,null,area))).isEqualTo(5);
        assertThat(needPolicy.resolve(merchant,category,area).maxResponseItems()).isEqualTo(2);
        assertThat(needPolicy.resolve(null,category,area).maxResponseItems()).isEqualTo(3);
        assertThat(needPolicy.resolve(null,null,area).maxResponseItems()).isEqualTo(5);
    }

    @Test void adminReadRoutesRejectOrdinaryUsersAndRemainReadOnly() throws Exception {
        String admin = adminBearer();
        String ordinary = "Bearer " + issuer.issue(activeUser("Settings reader").getId(), device()).accessToken().value();
        String path = "/api/v1/admin/settings/bulk.max_items";
        mvc.perform(patch(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":120}")).andExpect(status().isCreated());
        for (String route : java.util.List.of("/api/v1/admin/settings", path, path + "/history?scopeType=GLOBAL")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization", ordinary)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
            mvc.perform(get(route).header("Authorization", admin)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("private"), org.hamcrest.Matchers.containsString("no-store"))));
        }
        UUID settingId = jdbc.queryForObject("select id from system_settings where setting_key='bulk.max_items' and scope_type='GLOBAL'", UUID.class);
        assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?", Long.class, settingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='SYSTEM_SETTING_CHANGED' and entity_id=?", Long.class, settingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='SYSTEM_SETTING_CHANGED' and aggregate_id=?", Long.class, settingId)).isEqualTo(1);
    }

    @Test void historyPagesKeepEveryTiedTimestampEntryAndValidateBounds() throws Exception {
        String bearer = adminBearer(), path = "/api/v1/admin/settings/bulk.max_items";
        String etag = null;
        for (int value = 120; value < 126; value++) {
            var request = patch(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scopeType\":\"GLOBAL\",\"value\":" + value + "}");
            if (etag != null) request.header("If-Match", etag);
            var result = mvc.perform(request).andExpect(status().is(value == 120 ? 201 : 200)).andReturn();
            etag = result.getResponse().getHeader("ETag");
        }
        UUID settingId = jdbc.queryForObject("select id from system_settings where setting_key='bulk.max_items' and scope_type='GLOBAL'", UUID.class);
        jdbc.update("update system_setting_history set changed_at='2026-10-01T00:00:00Z' where setting_id=?", settingId);
        var expected = jdbc.queryForList("select id::text from system_setting_history where setting_id=? order by id desc", String.class, settingId);
        var observed = new java.util.ArrayList<String>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int page = 0; page < 3; page++) {
            var result = mvc.perform(get(path + "/history").header("Authorization", bearer)
                    .param("scopeType", "GLOBAL").param("size", "2").param("page", Integer.toString(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
                    .andExpect(jsonPath("$.content.length()").value(2)).andReturn();
            mapper.readTree(result.getResponse().getContentAsString()).path("content")
                    .forEach(row -> observed.add(row.path("id").asText()));
        }
        assertThat(observed).containsExactlyElementsOf(expected);
        mvc.perform(get(path + "/history").header("Authorization", bearer).param("scopeType", "GLOBAL")
                .param("size", "2").param("page", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.content.length()").value(0));
        for (String[] bounds : new String[][] {{"-1", "2"}, {"0", "0"}, {"0", "101"}}) {
            mvc.perform(get(path + "/history").header("Authorization", bearer).param("scopeType", "GLOBAL")
                    .param("page", bounds[0]).param("size", bounds[1])).andExpect(status().isUnprocessableEntity());
        }
        assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?", Long.class, settingId)).isEqualTo(6);
    }

    private String adminBearer() {
        var user=activeUser("Settings administrator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),user.getId(),SystemRole.ADMIN,Instant.now()));
        return "Bearer "+issuer.issue(user.getId(),device()).accessToken().value();
    }
}
