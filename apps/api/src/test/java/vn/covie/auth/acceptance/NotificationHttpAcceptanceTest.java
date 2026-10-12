package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.notifications.model.NotificationEntity;
import vn.covie.notifications.model.NotificationType;
import vn.covie.notifications.persistence.NotificationRepository;

@AutoConfigureMockMvc
class NotificationHttpAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired NotificationRepository notifications;

    @Test void inboxHttpTraversesTiedTimestampsAndRejectsInvalidPaging() throws Exception {
        var owner=activeUser("Paged inbox"); var other=activeUser("Foreign inbox");
        String bearer="Bearer "+issuer.issue(owner.getId(),device()).accessToken().value();
        String otherBearer="Bearer "+issuer.issue(other.getId(),device()).accessToken().value();
        Instant now=Instant.parse("2026-10-01T00:00:00Z");
        for(int index=0;index<5;index++) notifications.saveAndFlush(NotificationEntity.create(UUID.randomUUID(),owner.getId(),
                NotificationType.ORDER_COMPLETED,"Own","Own","ORDER",UUID.randomUUID(),
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),null,now));
        UUID foreign=UUID.randomUUID();
        notifications.saveAndFlush(NotificationEntity.create(foreign,other.getId(),NotificationType.ORDER_COMPLETED,"Foreign","Foreign",
                "ORDER",UUID.randomUUID(),com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),null,now));
        var expected=jdbc.queryForList("select id from notifications where user_id=? order by created_at desc,id desc",UUID.class,owner.getId());
        var seen=new java.util.ArrayList<UUID>();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String cursor=null,firstCursor=null;
        for(int page=0;page<3;page++) {
            var request=get("/api/v1/me/notifications").header("Authorization",bearer).param("limit","2");
            if(cursor!=null) request.param("cursor",cursor);
            var response=mvc.perform(request).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store, private")).andReturn();
            var json=mapper.readTree(response.getResponse().getContentAsString());
            assertThat(json.path("items").size()).isEqualTo(page<2?2:1);
            for(var row:json.path("items")) seen.add(UUID.fromString(row.path("id").asText()));
            cursor=json.path("nextCursor").isTextual()?json.path("nextCursor").asText():null;
            if(page==0) firstCursor=cursor;
            if(page<2) assertThat(cursor).isNotBlank(); else assertThat(cursor).isNull();
        }
        assertThat(seen).containsExactlyElementsOf(expected).doesNotHaveDuplicates().doesNotContain(foreign);
        var foreignPage=mvc.perform(get("/api/v1/me/notifications").header("Authorization",otherBearer)
                .param("cursor",firstCursor).param("limit","2")).andExpect(status().isOk()).andReturn();
        for(var row:mapper.readTree(foreignPage.getResponse().getContentAsString()).path("items"))
            assertThat(row.path("id").asText()).isEqualTo(foreign.toString());
        for(String limit:java.util.List.of("0","51")) mvc.perform(get("/api/v1/me/notifications")
                .header("Authorization",bearer).param("limit",limit)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization",bearer).param("cursor","!invalid"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id=? and read_at is not null",Long.class,owner.getId())).isZero();
    }

    @Test void readCommandsArePrivateOwnedAndPreserveExistingReadTimes() throws Exception {
        var owner=activeUser("Inbox owner"); var other=activeUser("Other inbox");
        String bearer="Bearer "+issuer.issue(owner.getId(),device()).accessToken().value();
        Instant now=Instant.parse("2026-10-01T00:00:00Z");
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),foreign=UUID.randomUUID();
        for(var id:java.util.List.of(first,second,foreign)) {
            notifications.saveAndFlush(NotificationEntity.create(id,id.equals(foreign)?other.getId():owner.getId(),
                    NotificationType.ORDER_COMPLETED,"PRIVATE_TITLE","PRIVATE_BODY","ORDER",UUID.randomUUID(),
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("deepLink","/orders/test"),null,now));
        }
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("COVIE-XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post("/api/v1/me/notifications/{id}/mark-read",first).cookie(csrf)
                .header("X-CSRF-Token",csrf.getValue())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/notifications/{id}/mark-read",foreign).header("Authorization",bearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/me/notifications/{id}/mark-read",UUID.randomUUID()).header("Authorization",bearer))
                .andExpect(status().isNotFound());
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/me/notifications/{id}/mark-read",first)
                .header("Authorization",bearer)).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control","no-store, private"));
        var readTime=jdbc.queryForObject("select read_at from notifications where id=?",java.sql.Timestamp.class,first);
        assertThat(readTime).isNotNull();
        assertThat(jdbc.queryForObject("select read_at from notifications where id=?",java.sql.Timestamp.class,second)).isNull();
        mvc.perform(post("/api/v1/me/notifications/mark-all-read").cookie(csrf)
                .header("X-CSRF-Token",csrf.getValue())).andExpect(status().isUnauthorized());
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/me/notifications/mark-all-read").header("Authorization",bearer))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store, private"));
        assertThat(jdbc.queryForObject("select read_at from notifications where id=?",java.sql.Timestamp.class,first)).isEqualTo(readTime);
        assertThat(jdbc.queryForObject("select read_at from notifications where id=?",java.sql.Timestamp.class,second)).isNotNull();
        assertThat(jdbc.queryForObject("select read_at from notifications where id=?",java.sql.Timestamp.class,foreign)).isNull();
        var inbox=mvc.perform(get("/api/v1/me/notifications").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store, private"))
                .andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        var rows=new com.fasterxml.jackson.databind.ObjectMapper().readTree(inbox.getResponse().getContentAsString()).path("items");
        for(var row:rows) {
            assertThat(row.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsOnly("id","type","data","read","createdAt");
            assertThat(row.path("read").asBoolean()).isTrue();
            assertThat(row.path("data").path("deepLink").asText()).isEqualTo("/orders/test");
        }
        assertThat(inbox.getResponse().getContentAsString()).doesNotContain(foreign.toString(),"PRIVATE_TITLE","PRIVATE_BODY");
    }
}
