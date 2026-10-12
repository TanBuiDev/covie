package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.IssuedSession;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.*;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.model.UserEntity;

/** Real signed JWTs, persisted sessions, security filters and controller/service/database boundaries. */
@AutoConfigureMockMvc
class AdminApiAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired MerchantProfileRepository merchants;
    @Autowired vn.covie.merchants.persistence.MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired OrderRepository orders;
    @Autowired vn.covie.orders.persistence.OrderEventRepository orderEvents;
    @Autowired vn.covie.moderation.persistence.ModerationCaseRepository moderationCases;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"users,CREATED_ASC", "users,CREATED_DESC", "users,UPDATED_DESC",
            "merchants,CREATED_ASC", "merchants,CREATED_DESC", "merchants,UPDATED_DESC",
            "orders,CREATED_ASC", "orders,CREATED_DESC", "orders,UPDATED_DESC"})
    void pagedAdminReadsDoNotRepeatOrLoseRowsWithIdenticalSortTimestamps(String domain, String sort) throws Exception {
        var admin = adminSession("Tied timestamp operator");
        String prefix = "Tie" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var expected = new java.util.ArrayList<String>();
        var now = java.sql.Timestamp.from(Instant.now().minusSeconds(60));
        UUID buyer = activeUser("Tied order buyer").getId();
        for (int index = 0; index < 12; index++) {
            UUID id;
            if (domain.equals("users")) {
                id = activeUser(prefix + " " + index).getId();
                jdbc.update("update users set created_at=?,updated_at=? where id=?", now, now, id);
            } else if (domain.equals("merchants")) {
                id = UUID.randomUUID();
                merchants.saveAndFlush(MerchantProfileEntity.create(id, prefix + " " + index, "tie-" + id, now.toInstant()));
            } else {
                id = order(buyer);
                jdbc.update("update orders set order_code=?,created_at=?,updated_at=? where id=?", prefix + index, now, now, id);
            }
            expected.add(id.toString());
        }
        var received = new java.util.ArrayList<String>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int page = 0; page < 6; page++) {
            var response = mvc.perform(get("/api/v1/admin/" + domain).header("Authorization", bearer(admin))
                            .param(domain.equals("orders") ? "orderCode" : "q", prefix)
                            .param("page", Integer.toString(page)).param("size", "2").param("sort", sort))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(12))
                    .andExpect(jsonPath("$.items.length()").value(2)).andReturn();
            for (var row : mapper.readTree(response.getResponse().getContentAsString()).path("items"))
                received.add(row.path("id").asText());
        }
        assertThat(received).containsExactlyInAnyOrderElementsOf(expected);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"audit-logs,CREATED_ASC", "audit-logs,CREATED_DESC",
            "moderation-cases,OPENED_ASC", "moderation-cases,OPENED_DESC", "moderation-cases,RESOLVED_DESC"})
    void pagedAuditAndModerationReadsKeepEveryTiedTimestampRow(String domain, String sort) throws Exception {
        var admin = adminSession("Tied audit operator");
        UUID actor = sessions.findById(admin.sessionId()).orElseThrow().getUserId();
        String requestId = "tie-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(60);
        var expected = new java.util.ArrayList<String>();
        for (int index = 0; index < 12; index++) {
            UUID id = UUID.randomUUID();
            if (domain.equals("audit-logs")) {
                audit.saveAndFlush(vn.covie.audit.AuditLogEntity.security(id, actor, "TIED_PAGE", "USER", actor,
                        requestId, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), now));
            } else {
                UUID merchant = UUID.randomUUID();
                merchants.saveAndFlush(MerchantProfileEntity.create(merchant, "Reported shop", "reported-" + merchant, now));
                moderationCases.saveAndFlush(vn.covie.moderation.model.ModerationCaseEntity.userReport(id, actor,
                        new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.MERCHANT, merchant),
                        "TIED_PAGE", "pagination fixture", now));
            }
            expected.add(id.toString());
        }
        var received = new java.util.ArrayList<String>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int page = 0; page < 6; page++) {
            var response = mvc.perform(get("/api/v1/admin/" + domain).header("Authorization", bearer(admin))
                            .param(domain.equals("audit-logs") ? "requestId" : "status", domain.equals("audit-logs") ? requestId : "OPEN")
                            .param("page", Integer.toString(page)).param("size", "2").param("sort", sort))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                    .andExpect(jsonPath("$.content.length()").value(2)).andReturn();
            for (var row : mapper.readTree(response.getResponse().getContentAsString()).path("content"))
                received.add(row.path("id").asText());
        }
        assertThat(received).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test void adminReadSurfacesRejectAnonymousAndOrdinaryUsers() throws Exception {
        var ordinary=issuer.issue(activeUser("Ordinary reader").getId(),device());
        var admin=adminSession("Admin reader");
        for(String path:new String[]{"/api/v1/admin/users","/api/v1/admin/merchants","/api/v1/admin/orders","/api/v1/admin/audit-logs","/api/v1/admin/settings","/api/v1/admin/moderation-cases","/api/v1/admin/ads"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",bearer(ordinary))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
            mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isOk());
        }
    }

    @Test void owningAnOrderDoesNotGrantAccessToItsAdminDetailRoute() throws Exception {
        UserEntity buyer=activeUser("Buyer owning order"); UUID order=order(buyer.getId());
        var session=issuer.issue(buyer.getId(),device());
        mvc.perform(get("/api/v1/orders/{id}",order).header("Authorization",bearer(session))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/orders/{id}",order).header("Authorization",bearer(session)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
    }

    @Test void adminDetailUsesReadOnlyAdminSemanticsEvenWhenTheAdminIsTheBuyer() throws Exception {
        var admin=adminSession("Admin also buyer"); UUID buyer=sessions.findById(admin.sessionId()).orElseThrow().getUserId();
        UUID order=order(buyer);
        mvc.perform(get("/api/v1/orders/{id}",order).header("Authorization",bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").isNotEmpty());
        mvc.perform(get("/api/v1/admin/orders/{id}",order).header("Authorization",bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }

    @Test void suspendAndRestoreThroughAdminApiReplaysOnceAndNeverReactivatesOldCredentials() throws Exception {
        var admin=adminSession("Operator"); UserEntity target=activeUser("Target");
        var targetSession=issuer.issue(target.getId(),device());
        String key=UUID.randomUUID().toString(), path="/api/v1/admin/users/"+target.getId();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/suspend").header("Authorization",bearer(admin))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"AUDIT_TEST\",\"note\":\"private note\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
        mvc.perform(get("/api/v1/me").header("Authorization",bearer(targetSession))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_USER_SUSPENDED'",Long.class,target.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='ADMIN_USER_SUSPENDED'",Long.class,target.getId())).isEqualTo(1);
        String restoreKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/restore").header("Authorization",bearer(admin))
                .header("Idempotency-Key",restoreKey)).andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        mvc.perform(get("/api/v1/me").header("Authorization",bearer(targetSession))).andExpect(status().isUnauthorized());
        assertThat(sessions.findAllByUserIdOrderByCreatedAtDesc(target.getId())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='ADMIN_USER_RESTORED'",Long.class,target.getId())).isEqualTo(1);
    }

    @Test void adminUserPaginationFiltersAndRejectsUnboundedOrUnsupportedInputs() throws Exception {
        var admin=adminSession("Pagination operator"); String prefix="Audit-"+UUID.randomUUID();
        UserEntity first=activeUser(prefix+" A"), second=activeUser(prefix+" B");
        var secretSession=issuer.issue(first.getId(),device());
        var page0=mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer(admin))
                .param("q",prefix).param("size","1").param("page","0").param("sort","CREATED_ASC"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        var page1=mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer(admin))
                .param("q",prefix).param("size","1").param("page","1").param("sort","CREATED_ASC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(java.util.List.of(json.readTree(page0.getResponse().getContentAsString()).path("items").get(0).path("id").asText(),
                json.readTree(page1.getResponse().getContentAsString()).path("items").get(0).path("id").asText()))
                .containsExactlyInAnyOrder(first.getId().toString(),second.getId().toString());
        for(String[] invalid:new String[][]{{"size","101"},{"size","0"},{"page","-1"},{"sort","passwordHash"}})
            mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer(admin)).param(invalid[0],invalid[1]))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        var detail=mvc.perform(get("/api/v1/admin/users/{id}",first.getId()).header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(secretSession.rawRefreshToken(),secretSession.accessToken().value(),"passwordHash","googleSubject");
    }

    @Test void adminOrderNamespaceHasNoLifecycleMutationRoutes() throws Exception {
        var admin=adminSession("Read only operator"); UUID buyer=sessions.findById(admin.sessionId()).orElseThrow().getUserId();
        UUID id=order(buyer);
        for(String action:new String[]{"confirm","cancel","complete"})
            mvc.perform(post("/api/v1/admin/orders/"+id+"/"+action).header("Authorization",bearer(admin))
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/orders/{id}",id)
                .header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?",Long.class,id)).isZero();
    }

    @Test void userStatusFilterCombinesWithTrimmedCaseInsensitiveNameSearch() throws Exception {
        var admin=adminSession("User filter operator");
        String prefix="uf-"+UUID.randomUUID().toString().substring(0,8);
        var active=activeUser(prefix+" Active");
        var suspended=activeUser(prefix+" Suspended");
        activeUser("Unrelated user");
        jdbc.update("update users set account_status='SUSPENDED' where id=?",suspended.getId());
        for(var entry:java.util.Map.of("ACTIVE",active.getId(),"SUSPENDED",suspended.getId()).entrySet()) {
            mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer(admin))
                    .param("status",entry.getKey()).param("q","  "+prefix.toUpperCase()+"  "))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(entry.getValue().toString()));
        }
    }

    @Test void adminDetailsAndOrderEventsReportMissingResourcesWithoutExposingRows() throws Exception {
        var admin=adminSession("Missing detail operator");
        UUID absent=UUID.randomUUID();
        for(String domain:new String[]{"users","merchants","orders"}) {
            mvc.perform(get("/api/v1/admin/"+domain+"/"+absent).header("Authorization",bearer(admin)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(get("/api/v1/admin/orders/"+absent+"/events").header("Authorization",bearer(admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        UUID existing=order(activeUser("Event buyer").getId());
        mvc.perform(get("/api/v1/admin/orders/"+existing+"/events").header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.length()").value(0));
        var merchant=orders.findById(existing).orElseThrow().getMerchantProfileId();
        jdbc.update("update merchant_profiles set deleted_at=now() where id=?",merchant);
        mvc.perform(get("/api/v1/admin/merchants/"+merchant).header("Authorization",bearer(admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test void merchantFiltersCombineStatusModerationAndSearchAndExcludeDeletedRows() throws Exception {
        var admin=adminSession("Merchant filter operator");
        String prefix="mf-"+UUID.randomUUID().toString().substring(0,8);
        UUID matching=null;
        for(int index=0;index<5;index++) {
            UUID id=UUID.randomUUID();
            merchants.saveAndFlush(MerchantProfileEntity.create(id,"Filter shop "+index,prefix+"-"+index,Instant.now()));
            jdbc.update("update merchant_profiles set merchant_status=?,moderation_status=? where id=?",
                    index==1 ? "SUSPENDED" : "ACTIVE",index==2 ? "HIDDEN" : "APPROVED",id);
            if(index==3) jdbc.update("update merchant_profiles set deleted_at=now() where id=?",id);
            if(index==4) jdbc.update("update merchant_profiles set slug=? where id=?","other-"+id,id);
            if(index==0) matching=id;
        }
        mvc.perform(get("/api/v1/admin/merchants").header("Authorization",bearer(admin))
                .param("status","ACTIVE").param("moderationStatus","APPROVED").param("q","  "+prefix.toUpperCase()+"  "))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matching.toString()));
        mvc.perform(get("/api/v1/admin/merchants").header("Authorization",bearer(admin))
                .param("status","SUSPENDED").param("q",prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/admin/merchants").header("Authorization",bearer(admin))
                .param("moderationStatus","HIDDEN").param("q",prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].moderationStatus").value("HIDDEN"));
    }

    @Test void orderFiltersCombineEverySupportedDimensionAndPreserveEmptyPageTotals() throws Exception {
        var admin=adminSession("Order filter operator");
        UUID buyer=activeUser("Filter buyer").getId(),otherBuyer=activeUser("Other buyer").getId();
        String prefix="OF"+UUID.randomUUID().toString().substring(0,8);
        UUID[] ids=new UUID[6];
        for(int index=0;index<ids.length;index++) {
            ids[index]=order(index==3 ? otherBuyer : buyer);
            jdbc.update("update orders set order_code=? where id=?",prefix+index,ids[index]);
        }
        var matching=orders.findById(ids[0]).orElseThrow();
        for(int index=1;index<ids.length;index++) {
            if(index!=4) jdbc.update("update orders set merchant_profile_id=?,merchant_location_id=? where id=?",
                    matching.getMerchantProfileId(),matching.getMerchantLocationId(),ids[index]);
        }
        jdbc.update("update orders set status='PENDING_CONFIRMATION' where id=?",ids[1]);
        jdbc.update("update orders set order_type='SERVICE',shipping_fee_type='NOT_APPLICABLE',shipping_fee=null where id=?",ids[2]);
        jdbc.update("update orders set order_code='DIFFERENT-CODE' where id=?",ids[5]);
        for(int page=0;page<2;page++) {
            var response=mvc.perform(get("/api/v1/admin/orders").header("Authorization",bearer(admin))
                    .param("status","CONFIRMED").param("type","PRODUCT")
                    .param("merchantId",matching.getMerchantProfileId().toString()).param("buyerUserId",buyer.toString())
                    .param("orderCode","  "+prefix+"  ").param("size","1").param("page",Integer.toString(page)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                    .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(1));
            if(page==0) response.andExpect(jsonPath("$.items[0].id").value(ids[0].toString()));
            else response.andExpect(jsonPath("$.items.length()").value(0));
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where order_code like ?",Long.class,prefix+"%")).isEqualTo(5);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"users","merchants","orders"})
    void typedAdminFiltersRejectMalformedInputWithStructuredErrors(String domain) throws Exception {
        var admin=adminSession("Malformed filter operator");
        var invalid=new java.util.ArrayList<String[]>();
        invalid.add(new String[]{"status","NOT_A_STATUS"});
        invalid.add(new String[]{"page","not-a-number"});
        invalid.add(new String[]{"size","101"});
        invalid.add(new String[]{"sort","UNSUPPORTED"});
        if(domain.equals("merchants")) invalid.add(new String[]{"moderationStatus","NOT_A_STATUS"});
        if(domain.equals("orders")) {
            invalid.add(new String[]{"type","NOT_A_TYPE"});
            invalid.add(new String[]{"merchantId","not-a-uuid"});
            invalid.add(new String[]{"buyerUserId","not-a-uuid"});
        }
        for(var parameter:invalid) mvc.perform(get("/api/v1/admin/"+domain).header("Authorization",bearer(admin))
                .param(parameter[0],parameter[1])).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test void populatedAdminOrderEventsAreChronologicalScopedAndExcludePrivatePayloads() throws Exception {
        var admin=adminSession("Event projection operator");
        var buyer=activeUser("Event history buyer");
        UUID selected=order(buyer.getId()),other=order(buyer.getId());
        Instant tied=Instant.parse("2026-10-01T00:00:00Z");
        UUID created=UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID confirmed=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ready=UUID.fromString("00000000-0000-0000-0000-000000000002");
        String secret="private-event-note-"+UUID.randomUUID();
        var metadata=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("privateBuyerContact",secret).put("internalNote",secret);
        // Insert in reverse chronology and reverse UUID order to test repository ordering.
        orderEvents.saveAndFlush(OrderEventEntity.lifecycle(ready,selected,OrderEventType.SELLER_MARKED_READY_FOR_PICKUP,
                buyer.getId(),OrderActorContext.MERCHANT,OrderStatus.CONFIRMED,OrderStatus.READY_FOR_PICKUP,
                "READY",secret,metadata,tied.plusSeconds(1)));
        orderEvents.saveAndFlush(OrderEventEntity.created(created,selected,buyer.getId(),metadata,tied));
        orderEvents.saveAndFlush(OrderEventEntity.lifecycle(confirmed,selected,OrderEventType.SELLER_CONFIRMED,
                buyer.getId(),OrderActorContext.MERCHANT,OrderStatus.PENDING_CONFIRMATION,OrderStatus.CONFIRMED,
                null,secret,metadata,tied));
        orderEvents.saveAndFlush(OrderEventEntity.lifecycle(UUID.randomUUID(),other,OrderEventType.ORDER_EXPIRED,
                null,OrderActorContext.SYSTEM,OrderStatus.PENDING_CONFIRMATION,OrderStatus.EXPIRED,
                "TIMEOUT",secret,metadata,tied.minusSeconds(1)));
        String body=mvc.perform(get("/api/v1/admin/orders/"+selected+"/events").header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(confirmed.toString()))
                .andExpect(jsonPath("$[1].id").value(created.toString()))
                .andExpect(jsonPath("$[2].id").value(ready.toString()))
                .andExpect(jsonPath("$[0].type").value("SELLER_CONFIRMED"))
                .andExpect(jsonPath("$[0].actorContext").value("MERCHANT"))
                .andExpect(jsonPath("$[0].fromStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$[0].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[2].reasonCode").value("READY"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(secret,"metadata","privateBuyerContact","internalNote","\"note\"",other.toString());
        assertThat(orderEvents.findAllByOrderIdOrderByCreatedAtAscIdAsc(selected)).hasSize(3);
    }

    @Test void buyerOwnershipNeverGrantsAdminEventHistoryAndRevokedAdminSessionIsRejected() throws Exception {
        var buyer=activeUser("Event access buyer");
        var buyerSession=issuer.issue(buyer.getId(),device());
        var admin=adminSession("Event access operator");
        UUID selected=order(buyer.getId());
        String path="/api/v1/admin/orders/"+selected+"/events";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization",bearer(buyerSession))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/orders/"+UUID.randomUUID()+"/events").header("Authorization",bearer(buyerSession)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isOk());
        sessionManagement.revokeForLogout(admin.sessionId());
        mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ACTIVE","PAUSED"})
    void merchantSuspensionRestoresOriginalStateAndReplaysWithoutDuplicateEffects(String initial) throws Exception {
        var admin=adminSession("Merchant safety operator");
        var owner=activeUser("Merchant session owner");
        var ownerSession=issuer.issue(owner.getId(),device());
        UUID id=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(id,"Safety shop","safety-"+id,Instant.now()));
        memberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(),id,
                owner.getId(),vn.covie.merchants.model.MerchantRole.OWNER,Instant.now()));
        jdbc.update("update merchant_profiles set merchant_status=?,moderation_status='HIDDEN' where id=?",initial,id);
        String path="/api/v1/admin/merchants/"+id,key=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/suspend").header("Authorization",bearer(admin))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"SAFETY_TEST\",\"note\":\"private operator note\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchantStatus").value("SUSPENDED"))
                .andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(post(path+"/suspend").header("Authorization",bearer(admin)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"CHANGED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST"));
        mvc.perform(post(path+"/suspend").header("Authorization",bearer(admin)).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SAFETY_TEST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ADMIN_MERCHANT_STATE_CONFLICT"));
        String restore=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/restore").header("Authorization",bearer(admin))
                .header("Idempotency-Key",restore)).andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantStatus").value(initial));
        assertThat(merchants.findById(id).orElseThrow().getModerationStatus().name()).isEqualTo("HIDDEN");
        mvc.perform(get("/api/v1/me").header("Authorization",bearer(ownerSession))).andExpect(status().isOk());
        for(String operation:new String[]{"SUSPENDED","RESTORED"}) {
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action=?",Long.class,id,"ADMIN_MERCHANT_"+operation)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type=?",Long.class,id,"ADMIN_MERCHANT_"+operation)).isEqualTo(1);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"users","merchants"})
    void unauthorizedInvalidAndImpossibleAdminCommandsLeaveStateAndSideEffectsUnchanged(String domain) throws Exception {
        var admin=adminSession("Rejected command operator");
        var ordinary=issuer.issue(activeUser("Ordinary command caller").getId(),device());
        UUID id;
        if(domain.equals("users")) id=activeUser("Rejected user target").getId();
        else {
            id=UUID.randomUUID(); merchants.saveAndFlush(MerchantProfileEntity.create(id,"Rejected shop","rejected-"+id,Instant.now()));
        }
        String path="/api/v1/admin/"+domain+"/"+id;
        mvc.perform(post(path+"/suspend").header("Authorization",bearer(ordinary)).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"TEST\"}"))
                .andExpect(status().isForbidden());
        for(String invalid:new String[]{"{\"reasonCode\":\" \"}","{\"reasonCode\":\""+"X".repeat(61)+"\"}",
                "{\"reasonCode\":\"TEST\",\"note\":\""+"X".repeat(2001)+"\"}"}) {
            mvc.perform(post(path+"/suspend").header("Authorization",bearer(admin)).header("Idempotency-Key",UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post(path+"/restore").header("Authorization",bearer(admin)).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(domain.equals("users") ? "ADMIN_USER_STATE_CONFLICT" : "ADMIN_MERCHANT_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/admin/"+domain+"/"+UUID.randomUUID()+"/suspend").header("Authorization",bearer(admin))
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"TEST\"}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,id)).isZero();
        if(domain.equals("users")) assertThat(users.findById(id).orElseThrow().getAccountStatus().name()).isEqualTo("ACTIVE");
        else assertThat(merchants.findById(id).orElseThrow().getMerchantStatus().name()).isEqualTo("ACTIVE");
    }

    private IssuedSession adminSession(String name) {
        UserEntity admin=activeUser(name);
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        return issuer.issue(admin.getId(),device());
    }
    private String bearer(IssuedSession session) { return "Bearer "+session.accessToken().value(); }
    private UUID order(UUID buyer) {
        Instant now=Instant.now(); UUID merchant=UUID.randomUUID(),location=UUID.randomUUID(),id=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Acceptance shop","shop-"+merchant,now));
        locations.saveAndFlush(MerchantLocationEntity.create(location,merchant,"Main","1 CoVie",10.8,106.7,now));
        var order=OrderEntity.pending(id,"CV"+id.toString().substring(0,8),OrderType.PRODUCT,buyer,merchant,location,
                PaymentMethod.COD,10000,OrderShippingFeeType.FREE,0L,10000,10000L,"VND",now.plusSeconds(600),now);
        order.confirm(now); orders.saveAndFlush(order); return id;
    }
}
