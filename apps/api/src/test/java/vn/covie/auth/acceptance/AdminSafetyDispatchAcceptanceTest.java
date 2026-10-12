package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.*;
import vn.covie.outbox.application.OutboxProcessor;
import vn.covie.realtime.*;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.*;

/** Actual commands, persisted notifications/deliveries and real SSE emitter; spy observes commit boundary only. */
@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class AdminSafetyDispatchAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired OutboxProcessor processor;
    @MockitoSpyBean SseSignalPublisher publisher;
    UUID expectedEvent;
    final List<SseSignal> sent=new ArrayList<>();

    @BeforeEach void isolateQueueAndObserveCommittedPublication() {
        jdbc.execute("truncate table outbox_events cascade");
        sent.clear();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,expectedEvent)).isEqualTo("PUBLISHED");
            Collection<SseSignal> signals=invocation.getArgument(0);
            for(var signal:signals) assertThat(jdbc.queryForObject(
                    "select count(*) from notifications where source_outbox_event_id=? and user_id=?",Long.class,expectedEvent,signal.userId())).isGreaterThan(0);
            sent.addAll(signals);
            return invocation.callRealMethod();
        }).when(publisher).publish(org.mockito.ArgumentMatchers.anyCollection());
    }

    private String token(UserEntity user) { return "Bearer "+issuer.issue(user.getId(),device()).accessToken().value(); }
    private String admin() {
        var user=activeUser("Dispatch administrator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),user.getId(),SystemRole.ADMIN,Instant.now()));
        return token(user);
    }
    private void dispatch(UUID aggregate,String eventType) {
        expectedEvent=jdbc.queryForObject("select id from outbox_events where aggregate_id=? and event_type=?",UUID.class,aggregate,eventType);
        assertThat(processor.processOne()).isTrue();
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,expectedEvent)).isEqualTo("PUBLISHED");
    }
    private void replayPublishedEvent() {
        int before=sent.size();
        jdbc.update("update outbox_events set status='PENDING',published_at=null where id=?",expectedEvent);
        assertThat(processor.processOne()).isTrue();
        assertThat(sent).hasSize(before);
    }
    private void membership(UUID merchant,UserEntity user,MerchantRole role) {
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,user.getId(),role,Instant.now()));
    }

    @Test void merchantSafetyDispatchesOnlyActiveOwnersAndManagersAfterCommitAndDeduplicates() throws Exception {
        String operator=admin();
        var owner=activeUser("Dispatch owner"); var manager=activeUser("Dispatch manager");
        var staff=activeUser("Dispatch staff"); var revoked=activeUser("Revoked manager"); var outsider=activeUser("Other merchant owner");
        UUID merchant=UUID.randomUUID(),other=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Dispatch shop","dispatch-"+merchant,Instant.now()));
        merchants.saveAndFlush(MerchantProfileEntity.create(other,"Other shop","other-"+other,Instant.now()));
        membership(merchant,owner,MerchantRole.OWNER); membership(merchant,manager,MerchantRole.MANAGER);
        membership(merchant,staff,MerchantRole.STAFF); membership(merchant,revoked,MerchantRole.MANAGER); membership(other,outsider,MerchantRole.OWNER);
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=? and user_id=?",merchant,revoked.getId());
        var streams=new ArrayList<MvcResult>();
        try {
            for(var user:List.of(owner,manager,staff,revoked,outsider)) streams.add(mvc.perform(get("/api/v1/me/events")
                    .header("Authorization",token(user))).andExpect(status().isOk()).andReturn());
            for(String operation:List.of("suspend","restore")) {
                String key=UUID.randomUUID().toString();
                for(int attempt=0;attempt<2;attempt++) {
                    var request=post("/api/v1/admin/merchants/"+merchant+"/"+operation).header("Authorization",operator).header("Idempotency-Key",key);
                    if(operation.equals("suspend")) request.contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"TEST\",\"note\":\"private safety note\"}");
                    mvc.perform(request).andExpect(status().isOk());
                }
                dispatch(merchant,"ADMIN_MERCHANT_"+(operation.equals("suspend") ? "SUSPENDED" : "RESTORED"));
                assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=?",UUID.class,expectedEvent))
                        .containsExactlyInAnyOrder(owner.getId(),manager.getId());
                assertThat(jdbc.queryForObject("select count(*) from notification_deliveries d join notifications n on n.id=d.notification_id where n.source_outbox_event_id=?",Long.class,expectedEvent)).isEqualTo(2);
                assertThat(jdbc.queryForList("select payload::text from notifications where source_outbox_event_id=?",String.class,expectedEvent))
                        .allSatisfy(payload -> assertThat(payload).contains(merchant.toString()).doesNotContain("private safety note"));
                replayPublishedEvent();
                assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?",Long.class,expectedEvent)).isEqualTo(2);
                assertThat(jdbc.queryForObject("select count(*) from notification_deliveries d join notifications n on n.id=d.notification_id where n.source_outbox_event_id=?",Long.class,expectedEvent)).isEqualTo(2);
            }
            assertThat(sent).hasSize(4).allSatisfy(signal -> assertThat(signal.eventName()).isEqualTo("merchant.updated"));
            for(int index=0;index<streams.size();index++) {
                var body=streams.get(index).getResponse().getContentAsString();
                if(index<2) {
                    assertThat(body).contains(merchant.toString(),"merchant.updated");
                    assertThat(body.lines().filter(line -> line.equals("event:merchant.updated")).count()).isEqualTo(2);
                } else assertThat(body).doesNotContain(merchant.toString(),"merchant.updated");
            }
        } finally { for(var stream:streams) stream.getRequest().getAsyncContext().complete(); }
    }

    @Test void userSuspensionCreatesNoNotificationAndRestoreNotifiesOnlyTargetWithoutRevivingOldSession() throws Exception {
        String operator=admin(); var target=activeUser("Dispatch user target");
        var old=issuer.issue(target.getId(),device());
        String path="/api/v1/admin/users/"+target.getId();
        mvc.perform(post(path+"/suspend").header("Authorization",operator).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"TEST\"}")).andExpect(status().isOk());
        dispatch(target.getId(),"ADMIN_USER_SUSPENDED");
        assertThat(sent).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?",Long.class,expectedEvent)).isZero();
        mvc.perform(post(path+"/restore").header("Authorization",operator).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+old.accessToken().value())).andExpect(status().isUnauthorized());
        var targetStream=mvc.perform(get("/api/v1/me/events").header("Authorization",token(target))).andExpect(status().isOk()).andReturn();
        var adminStream=mvc.perform(get("/api/v1/me/events").header("Authorization",operator)).andExpect(status().isOk()).andReturn();
        try {
            dispatch(target.getId(),"ADMIN_USER_RESTORED");
            assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=? and notification_type='ACCOUNT_RESTORED'",UUID.class,expectedEvent)).containsExactly(target.getId());
            replayPublishedEvent();
            assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?",Long.class,expectedEvent)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from notification_deliveries d join notifications n on n.id=d.notification_id where n.source_outbox_event_id=?",Long.class,expectedEvent)).isEqualTo(1);
            assertThat(sent).hasSize(1);
            assertThat(sent.getFirst().eventName()).isEqualTo("user.updated");
            assertThat(targetStream.getResponse().getContentAsString()).contains("user.updated",target.getId().toString());
            assertThat(targetStream.getResponse().getContentAsString().lines().filter(line -> line.equals("event:user.updated")).count()).isEqualTo(1);
            assertThat(adminStream.getResponse().getContentAsString()).doesNotContain("user.updated",target.getId().toString());
        } finally {
            targetStream.getRequest().getAsyncContext().complete(); adminStream.getRequest().getAsyncContext().complete();
        }
    }
}
