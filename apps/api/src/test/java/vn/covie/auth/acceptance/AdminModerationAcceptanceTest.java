package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.moderation.model.*;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

@AutoConfigureMockMvc
@org.springframework.boot.test.context.SpringBootTest(properties = "covie.outbox.max-events-per-poll=0")
@org.springframework.test.context.ActiveProfiles("test")
class AdminModerationAcceptanceTest {
    static final org.testcontainers.containers.PostgreSQLContainer<?> MODERATION_POSTGRES =
            new org.testcontainers.containers.PostgreSQLContainer<>(
                    org.testcontainers.utility.DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("moderation_test").withUsername("covie").withPassword("covie_test");
    static { MODERATION_POSTGRES.start(); }
    @org.springframework.test.context.DynamicPropertySource
    static void moderationDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MODERATION_POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", MODERATION_POSTGRES::getUsername);
        registry.add("spring.datasource.password", MODERATION_POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired vn.covie.users.persistence.UserRepository users;
    @Autowired vn.covie.users.persistence.SystemRoleAssignmentRepository roles;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired ModerationCaseRepository cases;
    @Autowired vn.covie.outbox.application.OutboxProcessor processor;
    @Autowired vn.covie.merchants.persistence.MerchantLocationRepository locations;
    @Autowired vn.covie.orders.persistence.OrderRepository orders;
    @Autowired vn.covie.orders.persistence.OrderItemRepository orderItems;
    @Autowired vn.covie.catalog.persistence.CatalogItemRepository catalogItems;
    @Autowired vn.covie.catalog.persistence.VariantRepository variants;
    @Autowired vn.covie.catalog.persistence.LocationVariantRepository offers;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    vn.covie.reviews.application.RatingProjectionService ratingProjection;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    vn.covie.needs.persistence.NeedRequestRepository needRepository;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager needEntities;
    private vn.covie.reviews.application.RatingProjectionService ratingTarget() {
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(ratingProjection);
    }

    @Test void approvalCannotOverwriteBuyerCancellationFromAnotherTransaction() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Lifecycle moderator"); var buyer=activeUser("Cancelling buyer");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String buyerBearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/moderation.mode")
                .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":\"PRE_APPROVAL\"}")).andExpect(status().isCreated());
        UUID category=UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Lifecycle category","lifecycle-"+category);
        var created=mvc.perform(post("/api/v1/me/need-requests").header("Authorization",buyerBearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PRODUCT\",\"categoryId\":\""+category+"\",\"title\":\"Lifecycle need\",\"queryText\":\"Coffee\",\"requestedQuantity\":1,\"lat\":10.8,\"lng\":106.7,\"radiusMeters\":5000,\"expiresAt\":\""+now.plusSeconds(3600)+"\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID need=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_need_request_id=?",UUID.class,need);
        var read=new java.util.concurrent.CountDownLatch(1); var cancelled=new java.util.concurrent.CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> observeRead=invocation -> {
            var entity=invocation.getMethod().getName().equals("lockById")
                    ? needEntities.find(vn.covie.needs.model.NeedRequestEntity.class,need,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                    : needEntities.find(vn.covie.needs.model.NeedRequestEntity.class,need);
            Object result=java.util.Optional.ofNullable(entity); read.countDown();
            // Unlocked reads permit cancellation to commit first; locked reads serialize it after approval.
            cancelled.await(700,java.util.concurrent.TimeUnit.MILLISECONDS);
            return result;
        };
        org.mockito.Mockito.doAnswer(observeRead).when(needRepository).findById(need);
        org.mockito.Mockito.doAnswer(observeRead).when(needRepository).lockById(need);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var approval=workers.submit(()-> mvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+"/approve")
                    .header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse());
            var cancellation=workers.submit(()-> {
                assertThat(read.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var response=mvc.perform(post("/api/v1/me/need-requests/"+need+"/cancel").header("Authorization",buyerBearer)
                        .header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse();
                cancelled.countDown(); return response;
            });
            assertThat(approval.get(10,java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            assertThat(cancellation.get(10,java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("select request_status from need_requests where id=?",String.class,need)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select moderation_status from need_requests where id=?",String.class,need)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select closed_at is not null from need_requests where id=?",Boolean.class,need)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='NEED_CANCELLED'",Long.class,need)).isEqualTo(1);
    }

    @org.junit.jupiter.api.BeforeEach void clearDatabase() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("moderation_test");
        jdbc.execute("truncate table audit_logs, auth_refresh_tokens, auth_action_tickets, auth_sessions, system_role_assignments, user_contact_methods, user_addresses, user_identities, users cascade");
    }
    private vn.covie.users.model.UserEntity activeUser(String name) {
        var user=users.saveAndFlush(vn.covie.users.model.UserEntity.active(UUID.randomUUID(),name,Instant.now()));
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),user.getId(),SystemRole.USER,Instant.now()));
        return user;
    }
    private vn.covie.auth.model.DeviceMetadata device() {
        return new vn.covie.auth.model.DeviceMetadata("Moderation browser","moderation-agent");
    }

    @Test void moderationCaseDetailIsAdminOnlyAndReadDoesNotMutateTheCase() throws Exception {
        var reporter=activeUser("Private reporter"); var outsider=activeUser("Other reader"); var admin=activeUser("Case reader");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        UUID merchant=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Reported shop","reported-"+merchant,Instant.now()));
        UUID caseId=UUID.randomUUID();
        cases.saveAndFlush(ModerationCaseEntity.userReport(caseId,reporter.getId(),new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),
                "AUDIT_TEST","PRIVATE_REPORT_DESCRIPTION_SENTINEL",Instant.now()));
        var before=jdbc.queryForMap("select * from moderation_cases where id=?",caseId);
        String path="/api/v1/admin/moderation-cases/"+caseId;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
        for(var user:java.util.List.of(reporter,outsider)) {
            String bearer="Bearer "+issuer.issue(user.getId(),device()).accessToken().value();
            var denied=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization",bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED")).andReturn();
            assertThat(denied.getResponse().getContentAsString()).doesNotContain("PRIVATE_REPORT_DESCRIPTION_SENTINEL",reporter.getId().toString());
        }
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.moderationCase.id").value(caseId.toString()))
                .andExpect(jsonPath("$.moderationCase.targetId").value(merchant.toString()))
                .andExpect(jsonPath("$.actions.length()").value(0)).andReturn();
        var summary=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("moderationCase");
        assertThat(summary.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsOnly("id","caseType","status","targetType","targetId","reasonCode","openedAt");
        assertThat(jdbc.queryForMap("select * from moderation_cases where id=?",caseId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isZero();
    }

    @Test void reportValidationRejectsInvalidFieldsWithoutConsumingTheCommandKey() throws Exception {
        var reporter=activeUser("Report boundaries"); UUID merchant=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Boundary shop","report-boundary-"+merchant,Instant.now()));
        String bearer="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        String key=UUID.randomUUID().toString();
        long existingEvents=jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_CASE_OPENED'",Long.class);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var valid=mapper.createObjectNode().put("targetType","MERCHANT").put("targetId",merchant.toString())
                .put("reasonCode","R".repeat(60)).put("note","N".repeat(2000));
        var invalid=new java.util.ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        var missingType=valid.deepCopy(); missingType.remove("targetType"); invalid.add(missingType);
        var missingId=valid.deepCopy(); missingId.remove("targetId"); invalid.add(missingId);
        invalid.add(valid.deepCopy().put("reasonCode"," "));
        invalid.add(valid.deepCopy().put("reasonCode","R".repeat(61)));
        invalid.add(valid.deepCopy().put("note","N".repeat(2001)));
        for(var body:invalid) {
            mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
            assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_CASE_OPENED'",Long.class)).isEqualTo(existingEvents);
        }
        var result=mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(valid)))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("caseId").asText());
        assertThat(jdbc.queryForObject("select reason_code from moderation_cases where id=?",String.class,caseId)).isEqualTo("R".repeat(60));
        assertThat(jdbc.queryForObject("select description from moderation_cases where id=?",String.class,caseId)).isEqualTo("N".repeat(2000));
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_CASE_OPENED'",Long.class)).isEqualTo(existingEvents+1);
    }

    @Test void missingReportIdempotencyKeyReturnsCommandValidationWithoutCreatingCase() throws Exception {
        var reporter=activeUser("Report validation"); UUID merchant=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Reported shop","report-key-"+merchant,Instant.now()));
        String bearer="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        mvc.perform(post("/api/v1/reports").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"MERCHANT\",\"targetId\":\""+merchant+"\",\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_merchant_profile_id=?",Long.class,merchant)).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"approve","reject","hide-content","restore-content","close","bulk-actions"})
    void missingModerationKeyUsesExistingValidationBeforeAnyMutation(String command) throws Exception {
        var admin=activeUser("Missing key admin");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        UUID merchant=UUID.randomUUID(),caseId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Case target","key-target-"+merchant,Instant.now()));
        cases.saveAndFlush(ModerationCaseEntity.userReport(caseId,admin.getId(),new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),"AUDIT_TEST","Key validation",Instant.now()));
        var before=jdbc.queryForMap("select * from moderation_cases where id=?",caseId);
        String path="/api/v1/admin/moderation-cases/"+(command.equals("bulk-actions")?command:caseId+"/"+command);
        String body=command.equals("bulk-actions")?"{\"action\":\"HIDE\",\"caseIds\":[\""+caseId+"\"],\"reasonCode\":\"AUDIT_TEST\"}":"{\"reasonCode\":\"AUDIT_TEST\"}";
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        var ordinary=activeUser("Ordinary moderator caller");
        String ordinaryBearer="Bearer "+issuer.issue(ordinary.getId(),device()).accessToken().value();
        mvc.perform(post(path).header("Authorization",ordinaryBearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
        assertThat(jdbc.queryForMap("select * from moderation_cases where id=?",caseId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isZero();
    }

    @Test void bulkValidationRejectsDuplicatesUnsupportedActionsAndEffectiveLimitBeforeMutation() throws Exception {
        var admin=activeUser("Bulk validation");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        UUID caseId=UUID.randomUUID();
        String path="/api/v1/admin/moderation-cases/bulk-actions";
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(post(path).header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of(
                        "action","HIDE","caseIds",java.util.List.of(caseId,caseId),"reasonCode","AUDIT_TEST"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path).header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of(
                        "action","APPROVE","caseIds",java.util.List.of(caseId),"reasonCode","AUDIT_TEST"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MODERATION_BULK_ACTION_NOT_SUPPORTED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/bulk.max_items")
                .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":1}")).andExpect(status().isCreated());
        mvc.perform(post(path).header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of(
                        "action","HIDE","caseIds",java.util.List.of(caseId,UUID.randomUUID()),"reasonCode","AUDIT_TEST"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='MODERATION_HIDE'",Long.class)).isZero();
    }

    @Test void concurrentSingleHideAndBulkHideCommitOneEffectiveAction() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Racing moderator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        UUID merchant=UUID.randomUUID(),caseId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Race shop","race-"+merchant,now));
        cases.saveAndFlush(ModerationCaseEntity.userReport(caseId,admin.getId(),
                new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),"AUDIT_TEST",null,now));
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var single=workers.submit(()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+"/hide-content")
                        .header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"AUDIT_TEST\"}")).andReturn();
            });
            var bulk=workers.submit(()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions")
                        .header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\",\"caseIds\":[\""+caseId+"\"],\"reasonCode\":\"AUDIT_TEST\"}")).andReturn();
            });
            var singleResponse=single.get(10,java.util.concurrent.TimeUnit.SECONDS).getResponse();
            var bulkResponse=bulk.get(10,java.util.concurrent.TimeUnit.SECONDS).getResponse();
            assertThat(singleResponse.getStatus()).isIn(200,409); assertThat(bulkResponse.getStatus()).isEqualTo(200);
            var result=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bulkResponse.getContentAsString()).path("results").get(0);
            assertThat((singleResponse.getStatus()==200?1:0)+(result.path("success").asBoolean()?1:0)).isEqualTo(1);
            if(!result.path("success").asBoolean()) assertThat(result.path("errorCode").asText()).isEqualTo("MODERATION_CASE_STATE_CONFLICT");
        }
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",Long.class,caseId)).isEqualTo(1);
        assertThat(merchants.findById(merchant).orElseThrow().getModerationStatus()).isEqualTo(MerchantModerationStatus.HIDDEN);
    }

    @Test void anotherCaseCannotHideAnAlreadyHiddenMerchantOrAppendAnAction() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Target conflict moderator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        UUID merchant=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Multiple reports","reports-"+merchant,now));
        for(UUID caseId:java.util.List.of(first,second)) cases.saveAndFlush(ModerationCaseEntity.userReport(caseId,admin.getId(),
                new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),"AUDIT_TEST",null,now));
        mvc.perform(post("/api/v1/admin/moderation-cases/"+first+"/hide-content").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"AUDIT_TEST\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/moderation-cases/"+second+"/hide-content").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"AUDIT_TEST\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MODERATION_TARGET_STATE_CONFLICT"));
        String bulkKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions")
                .header("Authorization",bearer).header("Idempotency-Key",bulkKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"caseIds\":[\""+second+"\"],\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[0].errorCode").value("MODERATION_TARGET_STATE_CONFLICT"));
        assertThat(cases.findById(second).orElseThrow().getCaseStatus()).isEqualTo(ModerationCaseStatus.OPEN);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id in (?,?)",Long.class,first,second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_ACTION_APPLIED' and aggregate_id in (?,?)",Long.class,first,second)).isEqualTo(1);
    }

    @Test void competingAdminsApproveAndRejectOnePendingNeedWithOneCommittedDecision() throws Exception {
        Instant now=Instant.now(); var firstAdmin=activeUser("Approver"); var secondAdmin=activeUser("Rejector"); var buyer=activeUser("Pending need buyer");
        for(var admin:java.util.List.of(firstAdmin,secondAdmin)) roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String approveBearer="Bearer "+issuer.issue(firstAdmin.getId(),device()).accessToken().value();
        String rejectBearer="Bearer "+issuer.issue(secondAdmin.getId(),device()).accessToken().value();
        String buyerBearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/moderation.mode")
                .header("Authorization",approveBearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":\"PRE_APPROVAL\"}")).andExpect(status().isCreated());
        UUID category=UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Race category","race-"+category);
        var created=mvc.perform(post("/api/v1/me/need-requests").header("Authorization",buyerBearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PRODUCT\",\"categoryId\":\""+category+"\",\"title\":\"Race need\",\"queryText\":\"Coffee\",\"requestedQuantity\":1,\"lat\":10.8,\"lng\":106.7,\"radiusMeters\":5000,\"expiresAt\":\""+now.plusSeconds(3600)+"\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID need=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_need_request_id=?",UUID.class,need);
        String path="/api/v1/admin/moderation-cases/"+caseId; var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var approve=workers.submit(()-> { start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post(path+"/approve").header("Authorization",approveBearer)
                        .header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse(); });
            var reject=workers.submit(()-> { start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post(path+"/reject").header("Authorization",rejectBearer)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"AUDIT_TEST\"}")).andReturn().getResponse(); });
            var approveResponse=approve.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var rejectResponse=reject.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(java.util.List.of(approveResponse.getStatus(),rejectResponse.getStatus())).containsExactlyInAnyOrder(200,409);
            var loser=approveResponse.getStatus()==409?approveResponse:rejectResponse;
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(loser.getContentAsString()).path("error").path("code").asText())
                    .isEqualTo("MODERATION_CASE_STATE_CONFLICT");
            assertThat(jdbc.queryForObject("select moderation_status from need_requests where id=?",String.class,need))
                    .isEqualTo(approveResponse.getStatus()==200?"APPROVED":"REJECTED");
        }
        assertThat(cases.findById(caseId).orElseThrow().getCaseStatus()).isEqualTo(ModerationCaseStatus.RESOLVED);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select request_status from need_requests where id=?",String.class,need)).isEqualTo("ACTIVE");
    }

    @org.junit.jupiter.api.AfterEach void cleanModerationFixtures() {
        org.mockito.Mockito.reset(ratingTarget());
        org.mockito.Mockito.reset(needRepository);
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("moderation_test");
        clearDatabase();
    }

    @Test void bulkHideKeepsSuccessfulItemsAndReplaysKnownFailureWithoutDuplicates() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Bulk moderator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        var ids=new java.util.ArrayList<UUID>();
        for(int index=0;index<3;index++) {
            UUID merchant=UUID.randomUUID(),caseId=UUID.randomUUID();
            merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Bulk shop","bulk-"+merchant,now));
            var moderationCase=ModerationCaseEntity.userReport(caseId,admin.getId(),
                    new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),"AUDIT_TEST",null,now);
            if(index==1) moderationCase.close(now);
            cases.saveAndFlush(moderationCase); ids.add(caseId);
        }
        String key=UUID.randomUUID().toString();
        String body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "action","HIDE","caseIds",ids,"reasonCode","AUDIT_TEST"));
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions")
                .header("Authorization",bearer).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[1].success").value(false))
                .andExpect(jsonPath("$.results[1].errorCode").value("MODERATION_CASE_STATE_CONFLICT"))
                .andExpect(jsonPath("$.results[2].success").value(true));
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='MODERATION_HIDE'",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_ACTION_APPLIED' and aggregate_id in (?,?,?)",Long.class,ids.get(0),ids.get(1),ids.get(2))).isEqualTo(2);
        String closeBody=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "action","CLOSE","caseIds",ids,"reasonCode","AUDIT_TEST"));
        mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions").header("Authorization",bearer)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[0].errorCode").value("MODERATION_CASE_STATE_CONFLICT"))
                .andExpect(jsonPath("$.results[2].success").value(false));
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isEqualTo(2);
    }

    @Test void catalogPreApprovalRevalidatesOffersAndPreservesPublicationOnReplay() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Catalog moderator"); var owner=activeUser("Catalog owner");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String ownerBearer="Bearer "+issuer.issue(owner.getId(),device()).accessToken().value();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/moderation.mode")
                .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":\"PRE_APPROVAL\"}")).andExpect(status().isCreated());
        UUID merchant=UUID.randomUUID(),category=UUID.randomUUID(),catalog=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Approval shop","approval-"+merchant,now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner.getId(),MerchantRole.OWNER,now));
        UUID location=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 CoVie",10.8,106.7,now)).getId();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Catalog category","catalog-"+category);
        catalogItems.saveAndFlush(vn.covie.catalog.model.CatalogItemEntity.create(catalog,merchant,
                vn.covie.catalog.model.CatalogItemType.PRODUCT,vn.covie.catalog.model.ListingKind.STANDARD,category,"Coffee","coffee-"+catalog,null,now));
        UUID variant=variants.saveAndFlush(vn.covie.catalog.model.VariantEntity.createDefault(UUID.randomUUID(),catalog,"Default",now)).getId();
        var offer=offers.saveAndFlush(vn.covie.catalog.model.LocationVariantEntity.create(UUID.randomUUID(),location,variant,25000,"VND",true,
                vn.covie.catalog.model.InventoryMode.UNTRACKED,null,now));
        mvc.perform(post("/api/v1/merchants/"+merchant+"/catalog-items/"+catalog+"/publish")
                .header("Authorization",ownerBearer).header("If-Match","\"v0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v1\""));
        assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,catalog)).isEqualTo("PENDING");
        UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL'",UUID.class,catalog);
        offer.update(25000,false,vn.covie.catalog.model.InventoryMode.UNTRACKED,null,now); offer=offers.saveAndFlush(offer);
        String path="/api/v1/admin/moderation-cases/"+caseId;
        mvc.perform(post(path+"/approve").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_LOCATION_OFFER_INVALID"));
        assertThat(cases.findById(caseId).orElseThrow().getCaseStatus()).isEqualTo(ModerationCaseStatus.OPEN);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isZero();
        offer.update(25000,true,vn.covie.catalog.model.InventoryMode.UNTRACKED,null,now); offers.saveAndFlush(offer);
        String approveKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/approve").header("Authorization",bearer)
                .header("Idempotency-Key",approveKey)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select listing_status from catalog_items where id=?",String.class,catalog)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,catalog)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",Long.class,caseId)).isEqualTo(1);
    }

    @Test void reviewHideRestoreReplayAndCompetingCasesAdjustBothRatingProjectionsExactlyOnce() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Review moderator"); var buyer=activeUser("Review buyer");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String buyerBearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        var reporter=activeUser("Review reporter"); String reporterBearer="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        UUID merchant=UUID.randomUUID(),category=UUID.randomUUID(),catalog=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Review shop","review-"+merchant,now));
        UUID location=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 CoVie",10.8,106.7,now)).getId();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Review category","review-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',?,?,0)",catalog,merchant,category,"Coffee","review-"+catalog,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        var order=vn.covie.orders.model.OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().substring(0,8),
                vn.covie.orders.model.OrderType.PRODUCT,buyer.getId(),merchant,location,vn.covie.shared.commerce.PaymentMethod.COD,
                100,vn.covie.orders.model.OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(600),now);
        order.complete(now); orders.saveAndFlush(order);
        var item=orderItems.saveAndFlush(vn.covie.orders.model.OrderItemEntity.create(UUID.randomUUID(),order.getId(),catalog,null,null,
                vn.covie.catalog.model.CatalogItemType.PRODUCT,1,100,"VND",com.fasterxml.jackson.databind.node.NullNode.getInstance(),now));
        var created=mvc.perform(post("/api/v1/me/reviews").header("Authorization",buyerBearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderItemId\":\""+item.getId()+"\",\"rating\":5,\"comment\":\"Good\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID review=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        assertRatings(catalog,merchant,1,5);
        var report=mvc.perform(post("/api/v1/reports").header("Authorization",reporterBearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"REVIEW\",\"targetId\":\""+review+"\",\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.getResponse().getContentAsString()).path("caseId").asText());
        String path="/api/v1/admin/moderation-cases/"+caseId,hideKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/hide-content").header("Authorization",bearer).header("Idempotency-Key",hideKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"AUDIT_TEST\"}")).andExpect(status().isOk());
        assertRatings(catalog,merchant,0,0);
        String restoreKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/restore-content").header("Authorization",bearer)
                .header("Idempotency-Key",restoreKey)).andExpect(status().isOk());
        assertRatings(catalog,merchant,1,5);
        assertThat(jdbc.queryForObject("select review_status from reviews where id=?",String.class,review)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(2);
        jdbc.update("update moderation_actions set created_at=?,note='ADMIN_ACTION_NOTE_ALLOWED',metadata='{\"private\":\"INTERNAL_ACTION_METADATA_SENTINEL\"}'::jsonb where moderation_case_id=?",
                java.sql.Timestamp.from(now),caseId);
        UUID otherCase=UUID.randomUUID(),otherAction=UUID.randomUUID();
        var unrelated=ModerationCaseEntity.userReport(otherCase,reporter.getId(),new ModerationTargetRef(ModerationTargetType.MERCHANT,merchant),"AUDIT_TEST","Foreign case",now);
        unrelated.close(now); cases.saveAndFlush(unrelated);
        jdbc.update("insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,note,metadata,created_at) values (?,?,'CLOSE',?,'AUDIT_TEST','FOREIGN_HISTORY_SENTINEL','{}'::jsonb,?)",
                otherAction,otherCase,admin.getId(),java.sql.Timestamp.from(now));
        var beforeHistory=jdbc.queryForList("select * from moderation_actions where moderation_case_id=? order by created_at,id",caseId);
        var detail=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.actions.length()").value(2)).andReturn();
        var detailJson=new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail.getResponse().getContentAsString());
        var returnedIds=new java.util.ArrayList<UUID>();
        var returnedActions=new java.util.ArrayList<String>();
        for(var action:detailJson.path("actions")) {
            assertThat(action.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrderElementsOf(action.path("action").asText().equals("HIDE")
                            ? java.util.List.of("id","action","actorAdminUserId","reasonCode","note","createdAt")
                            : java.util.List.of("id","action","actorAdminUserId","note","createdAt"));
            if(action.path("action").asText().equals("HIDE"))
                assertThat(action.path("reasonCode").asText()).isEqualTo("AUDIT_TEST");
            returnedIds.add(UUID.fromString(action.path("id").asText()));
            returnedActions.add(action.path("action").asText());
            assertThat(action.path("actorAdminUserId").asText()).isEqualTo(admin.getId().toString());
            assertThat(action.path("note").asText()).isEqualTo("ADMIN_ACTION_NOTE_ALLOWED");
        }
        assertThat(returnedIds).containsExactlyElementsOf(jdbc.queryForList("select id from moderation_actions where moderation_case_id=? order by created_at,id",UUID.class,caseId));
        assertThat(returnedActions).containsExactlyInAnyOrder("HIDE","RESTORE");
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("INTERNAL_ACTION_METADATA_SENTINEL","FOREIGN_HISTORY_SENTINEL",otherAction.toString());
        assertThat(jdbc.queryForList("select * from moderation_actions where moderation_case_id=? order by created_at,id",caseId)).isEqualTo(beforeHistory);
        UUID firstRace=UUID.randomUUID(),secondRace=UUID.randomUUID();
        for(UUID id:java.util.List.of(firstRace,secondRace)) cases.saveAndFlush(ModerationCaseEntity.userReport(id,reporter.getId(),
                new ModerationTargetRef(ModerationTargetType.REVIEW,review),"AUDIT_TEST",null,now));
        var reads=new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(invocation -> {
            // Force both unprotected transitions to observe ACTIVE before updating the real projections.
            // With target serialization, the first worker may proceed after this bounded wait.
            try { reads.await(500,java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch(java.util.concurrent.TimeoutException | java.util.concurrent.BrokenBarrierException ignored) { }
            return invocation.callRealMethod();
        }).when(ratingTarget()).onVisibilityTransition(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(vn.covie.reviews.model.ReviewStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(vn.covie.reviews.model.ReviewStatus.HIDDEN));
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.List<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>> outcomes=new java.util.ArrayList<>();
            for(UUID id:java.util.List.of(firstRace,secondRace)) outcomes.add(workers.submit(()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/admin/moderation-cases/"+id+"/hide-content").header("Authorization",bearer)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"AUDIT_TEST\"}")).andReturn().getResponse();
            }));
            var firstResponse=outcomes.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS);
            var secondResponse=outcomes.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(java.util.List.of(firstResponse.getStatus(),secondResponse.getStatus())).containsExactlyInAnyOrder(200,409);
            var loser=firstResponse.getStatus()==409?firstResponse:secondResponse;
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(loser.getContentAsString()).path("error").path("code").asText())
                    .isEqualTo("MODERATION_TARGET_STATE_CONFLICT");
        }
        UUID remaining=cases.findById(firstRace).orElseThrow().getCaseStatus()==ModerationCaseStatus.OPEN?firstRace:secondRace;
        String bulkRaceKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions")
                .header("Authorization",bearer).header("Idempotency-Key",bulkRaceKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"caseIds\":[\""+remaining+"\"],\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[0].errorCode").value("MODERATION_TARGET_STATE_CONFLICT"));
        assertRatings(catalog,merchant,0,0);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id in (?,?)",Long.class,firstRace,secondRace)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where id in (?,?) and case_status='OPEN'",Long.class,firstRace,secondRace)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='MODERATION_ACTION_APPLIED' and aggregate_id in (?,?)",Long.class,firstRace,secondRace)).isEqualTo(1);
    }
    private void assertRatings(UUID catalog,UUID merchant,long count,long sum) {
        assertThat(jdbc.queryForObject("select review_count from catalog_item_rating_stats where catalog_item_id=?",Long.class,catalog)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select rating_sum from catalog_item_rating_stats where catalog_item_id=?",Long.class,catalog)).isEqualTo(sum);
        assertThat(jdbc.queryForObject("select review_count from merchant_rating_stats where merchant_profile_id=?",Long.class,merchant)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select rating_sum from merchant_rating_stats where merchant_profile_id=?",Long.class,merchant)).isEqualTo(sum);
    }

    @Test void configuredPreApprovalOpensOneCaseAndAdminApprovalPreservesNeedLifecycle() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Approval moderator"); var buyer=activeUser("Need buyer");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String buyerBearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/moderation.mode")
                .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"GLOBAL\",\"value\":\"PRE_APPROVAL\"}"))
                .andExpect(status().isCreated());
        UUID category=UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Approval category","approval-"+category);
        String body="{\"type\":\"PRODUCT\",\"categoryId\":\""+category+"\",\"title\":\"Need coffee\",\"queryText\":\"Coffee\",\"requestedQuantity\":1,\"lat\":10.8,\"lng\":106.7,\"radiusMeters\":5000,\"expiresAt\":\""+now.plusSeconds(3600)+"\"}";
        String createKey=UUID.randomUUID().toString();
        var created=mvc.perform(post("/api/v1/me/need-requests").header("Authorization",buyerBearer)
                .header("Idempotency-Key",createKey).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        UUID need=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/me/need-requests").header("Authorization",buyerBearer)
                .header("Idempotency-Key",createKey).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select moderation_status from need_requests where id=?",String.class,need)).isEqualTo("PENDING");
        UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_need_request_id=? and case_type='PRE_APPROVAL'",UUID.class,need);
        String approveKey=UUID.randomUUID().toString(),path="/api/v1/admin/moderation-cases/"+caseId;
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/approve").header("Authorization",bearer)
                .header("Idempotency-Key",approveKey)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(post(path+"/reject").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MODERATION_CASE_STATE_CONFLICT"));
        assertThat(jdbc.queryForObject("select moderation_status from need_requests where id=?",String.class,need)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select request_status from need_requests where id=?",String.class,need)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='NEED_CREATED'",Long.class,need)).isEqualTo(1);
        UUID event=jdbc.queryForObject("select id from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",UUID.class,caseId);
        for(int attempt=0;attempt<100;attempt++) {
            if("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event))) break;
            processor.processOne();
        }
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=? and notification_type='MODERATION_UPDATED'",UUID.class,event))
                .containsExactly(buyer.getId());
    }

    @Test void merchantHideAndRestoreAreAuthorizedReplayOnceAndDispatchToTheOwner() throws Exception {
        Instant now=Instant.now(); var admin=activeUser("Moderator");
        var reporter=activeUser("Reporter"); var owner=activeUser("Owner");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        String ordinary="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        UUID merchant=UUID.randomUUID(),caseId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Moderated shop","moderated-"+merchant,now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner.getId(),MerchantRole.OWNER,now));
        String reportBody="{\"targetType\":\"MERCHANT\",\"targetId\":\""+merchant+"\",\"reasonCode\":\"AUDIT_TEST\",\"note\":\"Report fixture\"}";
        String reportKey=UUID.randomUUID().toString();
        var report=mvc.perform(post("/api/v1/reports").header("Authorization",ordinary).header("Idempotency-Key",reportKey)
                .contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN")).andReturn();
        caseId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.getResponse().getContentAsString()).path("caseId").asText());
        mvc.perform(post("/api/v1/reports").header("Authorization",ordinary).header("Idempotency-Key",reportKey)
                .contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.caseId").value(caseId.toString()));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
        String path="/api/v1/admin/moderation-cases/"+caseId;
        mvc.perform(post(path+"/hide-content").header("Authorization",ordinary).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
        mvc.perform(post(path+"/hide-content").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MODERATION_REASON_REQUIRED"));
        String hideKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/hide-content").header("Authorization",bearer)
                .header("Idempotency-Key",hideKey).contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isOk());
        assertThat(merchants.findById(merchant).orElseThrow().getModerationStatus()).isEqualTo(MerchantModerationStatus.HIDDEN);
        assertThat(cases.findById(caseId).orElseThrow().getCaseStatus()).isEqualTo(ModerationCaseStatus.RESOLVED);
        String restoreKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path+"/restore-content").header("Authorization",bearer)
                .header("Idempotency-Key",restoreKey)).andExpect(status().isOk());
        var restored=merchants.findById(merchant).orElseThrow();
        assertThat(restored.getModerationStatus()).isEqualTo(MerchantModerationStatus.APPROVED);
        assertThat(restored.getMerchantStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(2);
        var events=jdbc.queryForList("select id from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",UUID.class,caseId);
        assertThat(events).hasSize(2);
        for(UUID event:events) {
            for(int attempt=0;attempt<100;attempt++) {
                if("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event))) break;
                processor.processOne();
            }
            assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=? and notification_type='MODERATION_UPDATED'",UUID.class,event))
                    .containsExactly(owner.getId());
        }
    }

    @Test void hiddenMerchantsAndUnsupportedUserTargetsCannotCreateReports() throws Exception {
        var reporter=activeUser("Report restrictions");
        String bearer="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        UUID merchant=UUID.randomUUID();
        var hidden=MerchantProfileEntity.create(merchant,"Hidden shop","hidden-"+merchant,Instant.now());
        hidden.hide(Instant.now()); merchants.saveAndFlush(hidden);
        mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"MERCHANT\",\"targetId\":\""+merchant+"\",\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"USER\",\"targetId\":\""+reporter.getId()+"\",\"reasonCode\":\"AUDIT_TEST\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("REPORT_TARGET_TYPE_NOT_SUPPORTED"));
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_merchant_profile_id=?",Long.class,merchant)).isZero();
    }

    @Test void catalogReportsRequireVisibleItemAndMerchantAndReplayOneCase() throws Exception {
        var reporter=activeUser("Catalog reporter");
        String bearer="Bearer "+issuer.issue(reporter.getId(),device()).accessToken().value();
        Instant now=Instant.now(); UUID merchant=UUID.randomUUID(),category=UUID.randomUUID(),catalog=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Public shop","report-shop-"+merchant,now));
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,? ,?,'PRODUCT','ACTIVE',0,0)",category,"Category","report-category-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',?,?,0)",catalog,merchant,category,"Public item","report-item-"+catalog,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        String body="{\"targetType\":\"CATALOG_ITEM\",\"targetId\":\""+catalog+"\",\"reasonCode\":\"AUDIT_TEST\"}";
        String key=UUID.randomUUID().toString(); String first=null;
        for(int attempt=0;attempt<2;attempt++) {
            var response=mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control","private, no-store")).andReturn();
            if(first==null) first=response.getResponse().getContentAsString(); else assertThat(response.getResponse().getContentAsString()).isEqualTo(first);
        }
        UUID caseId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).path("caseId").asText());
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,catalog)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reporter_user_id from moderation_cases where id=?",UUID.class,caseId)).isEqualTo(reporter.getId());
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,caseId)).isEqualTo(1L);
        for(String state:java.util.List.of("HIDDEN","DRAFT","MERCHANT_HIDDEN")) {
            if(state.equals("MERCHANT_HIDDEN")) {
                jdbc.update("update catalog_items set listing_status='ACTIVE',moderation_status='APPROVED' where id=?",catalog);
                jdbc.update("update merchant_profiles set moderation_status='HIDDEN' where id=?",merchant);
            } else if(state.equals("DRAFT")) jdbc.update("update catalog_items set listing_status='DRAFT',moderation_status='APPROVED' where id=?",catalog);
            else jdbc.update("update catalog_items set moderation_status='HIDDEN' where id=?",catalog);
            mvc.perform(post("/api/v1/reports").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,catalog)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,caseId)).isEqualTo(1L);
    }
}
