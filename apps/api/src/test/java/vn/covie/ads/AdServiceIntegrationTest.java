package vn.covie.ads;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.security.*;
import vn.covie.shared.security.*;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.ads.api.*;
import vn.covie.ads.api.AdCampaignRequest.*;
import vn.covie.ads.api.AdCampaignResponse.Status;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.model.UserEntity;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@AutoConfigureMockMvc
class AdServiceIntegrationTest extends PostgresIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired vn.covie.media.persistence.MediaAssetRepository media;
 @Autowired AdService ads;@Autowired JdbcTemplate jdbc;@Autowired UserRepository users;
 @MockitoBean CurrentActorProvider actors;@MockitoBean MerchantAccessPolicy merchants;@MockitoBean AppClock clock;
 Instant now=Instant.parse("2026-10-08T00:00:00Z");UUID user,merchant;
 @BeforeEach void setup(){user=UUID.randomUUID();merchant=UUID.randomUUID();users.saveAndFlush(UserEntity.active(user,"Ad admin",now));when(clock.now()).thenReturn(now);when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(SystemRole.ADMIN),UUID.randomUUID()));jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values(?,?,?,'ACTIVE','APPROVED',?,?)",merchant,"Ad store",merchant.toString(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));when(merchants.require(merchant,MerchantCapability.MANAGE_CATALOG_STRUCTURE)).thenReturn(new MerchantAccess(user,merchant,UUID.randomUUID(),vn.covie.merchants.model.MerchantRole.OWNER));}
 AdCampaignRequest request(String target,int priority){return new AdCampaignRequest("Sale","Body","View",target,CreativeType.CSS,Template.FLASH,null,Placement.IN_FEED,Device.ALL,now.minusSeconds(1),now.plusSeconds(3600),true,priority);}
 @Test void deduplicatesDeliveryEventsAndAggregatesUtcDailyCtr(){
  var campaign=ads.create(null,request("/search",1000));var delivery=ads.active(Placement.IN_FEED,Device.MOBILE).stream().filter(a->a.id().equals(campaign.id())).findFirst().orElseThrow();
  assertThat(jdbc.queryForObject("select campaign_id from ad_deliveries where id=?",UUID.class,UUID.fromString(delivery.deliveryToken()))).isEqualTo(campaign.id());
  when(clock.now()).thenReturn(now.plusSeconds(2));
  ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.IMPRESSION,1000));
  ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken().toUpperCase(java.util.Locale.ROOT),UUID.randomUUID(),AdEventRequest.Type.IMPRESSION,1000));
  var clickId=UUID.randomUUID();ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),clickId,AdEventRequest.Type.CLICK,0));ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),clickId,AdEventRequest.Type.CLICK,0));
  var stats=ads.stats(null,campaign.id(),LocalDate.parse("2026-10-08"),LocalDate.parse("2026-10-08"));assertThat(stats.impressions()).isEqualTo(1);assertThat(stats.clicks()).isEqualTo(1);assertThat(stats.ctr()).isEqualTo(100);assertThat(stats.days()).hasSize(1);
  assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken()+"x",UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(RuntimeException.class);
  when(clock.now()).thenReturn(now.plusSeconds(1801));assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(RuntimeException.class);
  ads.delete(null,campaign.id(),VersionPrecondition.format(campaign.version()));assertThat(ads.active(Placement.IN_FEED,Device.MOBILE)).noneMatch(a->a.id().equals(campaign.id()));
 }
 @Test void sellerEditsRequireResubmissionAndAdminApproval(){
  var draft=ads.create(merchant,request("/merchants/"+merchant,2));assertThat(draft.status()).isEqualTo(Status.DRAFT);
  var pending=ads.submit(merchant,draft.id(),VersionPrecondition.format(draft.version()));assertThat(pending.status()).isEqualTo(Status.PENDING);
  var approved=ads.review(draft.id(),VersionPrecondition.format(pending.version()),true,null);assertThat(ads.active(Placement.IN_FEED,Device.DESKTOP)).anyMatch(a->a.id().equals(draft.id()));
  var internalAdmin=ads.create(null,request("/merchants/"+merchant,3));
  jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?",merchant);
  assertThat(ads.active(Placement.IN_FEED,Device.DESKTOP)).noneMatch(a->a.id().equals(draft.id()));
  assertThat(ads.active(Placement.IN_FEED,Device.DESKTOP)).noneMatch(a->a.id().equals(internalAdmin.id()));
  jdbc.update("update merchant_profiles set merchant_status='ACTIVE' where id=?",merchant);
  var edited=ads.update(merchant,draft.id(),VersionPrecondition.format(approved.version()),request("/merchants/"+merchant,4));assertThat(edited.status()).isEqualTo(Status.DRAFT);assertThat(ads.active(Placement.IN_FEED,Device.DESKTOP)).noneMatch(a->a.id().equals(draft.id()));
  assertThatThrownBy(()->ads.update(merchant,draft.id(),VersionPrecondition.format(approved.version()),request("/merchants/"+merchant,5))).isInstanceOf(RuntimeException.class);
  var outsider=UUID.randomUUID();when(merchants.require(outsider,MerchantCapability.MANAGE_CATALOG_STRUCTURE)).thenThrow(new vn.covie.shared.web.ApiException(vn.covie.shared.web.ErrorCode.RESOURCE_NOT_FOUND,org.springframework.http.HttpStatus.NOT_FOUND,"Resource not found."));
  assertThatThrownBy(()->ads.list(outsider)).isInstanceOf(vn.covie.shared.web.ApiException.class);
 }
 @Test void adminAuthorizationAndScheduleGateDelivery(){
  var campaign=ads.create(null,request("/search",1000));
  when(clock.now()).thenReturn(now.plusSeconds(3600));assertThat(ads.active(Placement.IN_FEED,Device.DESKTOP)).noneMatch(a->a.id().equals(campaign.id()));
  when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(),UUID.randomUUID()));assertThatThrownBy(()->ads.list(null)).isInstanceOf(vn.covie.shared.web.ApiException.class);
 }
 @Test void publicHttpContractUsesDeliveryProofForTelemetryWhileAdminMutationsRequireCsrf() throws Exception {
  var campaign=ads.create(null,request("/search",1000));
  mvc.perform(get("/api/v1/ads/active").param("placement","IN_FEED").param("device","MOBILE"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$[0].id").value(campaign.id().toString()))
   .andExpect(jsonPath("$[0].merchantId").doesNotExist()).andExpect(jsonPath("$[0].ownerUserId").doesNotExist()).andExpect(jsonPath("$[0].reviewReason").doesNotExist());
  mvc.perform(get("/api/v1/admin/ads")).andExpect(status().isUnauthorized());
  var delivery=ads.active(Placement.IN_FEED,Device.MOBILE).stream().filter(a->a.id().equals(campaign.id())).findFirst().orElseThrow();
  String body="{\"deliveryToken\":\""+delivery.deliveryToken()+"\",\"eventId\":\""+UUID.randomUUID()+"\",\"type\":\"CLICK\",\"visibleMillis\":0}";
  mvc.perform(post("/api/v1/ads/"+campaign.id()+"/events").contentType("application/json").content(body)).andExpect(status().isNoContent());
  mvc.perform(post("/api/v1/admin/ads").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/ads/"+campaign.id()+"/events").with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isNoContent());
 }
 @Test void anotherAdminCanReplaceCreativeWithTheirOwnVerifiedUpload() {
  var first=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),user,"ads/"+UUID.randomUUID(),vn.covie.media.model.MediaPurpose.AD_BANNER,"image/png",8,now.plusSeconds(600),now);first.markReady(now);media.saveAndFlush(first);
  var source=request("/search",1000);var campaign=ads.create(null,new AdCampaignRequest(source.title(),source.body(),source.ctaLabel(),source.targetUrl(),CreativeType.IMAGE,null,first.getId(),source.placement(),source.device(),source.startsAt(),source.endsAt(),true,1000));
  UUID second=UUID.randomUUID();users.saveAndFlush(UserEntity.active(second,"Second admin",now));var replacement=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),second,"ads/"+UUID.randomUUID(),vn.covie.media.model.MediaPurpose.AD_BANNER,"image/png",8,now.plusSeconds(600),now);replacement.markReady(now);media.saveAndFlush(replacement);
  when(actors.currentActor()).thenReturn(new CurrentActor(second,Set.of(SystemRole.ADMIN),UUID.randomUUID()));
  var edited=ads.update(null,campaign.id(),VersionPrecondition.format(campaign.version()),new AdCampaignRequest(source.title(),source.body(),source.ctaLabel(),source.targetUrl(),CreativeType.IMAGE,null,replacement.getId(),source.placement(),source.device(),source.startsAt(),source.endsAt(),true,1000));assertThat(edited.mediaId()).isEqualTo(replacement.getId());
  var retained=ads.update(null,campaign.id(),VersionPrecondition.format(edited.version()),new AdCampaignRequest("Edited title",source.body(),source.ctaLabel(),source.targetUrl(),CreativeType.IMAGE,null,replacement.getId(),source.placement(),source.device(),source.startsAt(),source.endsAt(),true,1000));assertThat(retained.title()).isEqualTo("Edited title");
 }
 @Test void staleDisabledDeletedAndHiddenTargetDeliveriesCannotRecordTelemetry() {
  var campaign=ads.create(null,request("/merchants/"+merchant,1000));
  var delivery=ads.active(Placement.IN_FEED,Device.MOBILE).stream().filter(a->a.id().equals(campaign.id())).findFirst().orElseThrow();
  jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?",merchant);
  assertThat(ads.active(Placement.IN_FEED,Device.MOBILE)).noneMatch(a->a.id().equals(campaign.id()));
  assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(vn.covie.shared.web.ApiException.class);
  jdbc.update("update merchant_profiles set merchant_status='ACTIVE' where id=?",merchant);
  var edited=ads.update(null,campaign.id(),VersionPrecondition.format(campaign.version()),request("/merchants/"+merchant,999));
  assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(vn.covie.shared.web.ApiException.class);
  var fresh=ads.active(Placement.IN_FEED,Device.MOBILE).stream().filter(a->a.id().equals(campaign.id())).findFirst().orElseThrow();
  ads.event(campaign.id(),new AdEventRequest(fresh.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0));
  var source=request("/merchants/"+merchant,999);
  var disabled=ads.update(null,campaign.id(),VersionPrecondition.format(edited.version()),new AdCampaignRequest(source.title(),source.body(),source.ctaLabel(),source.targetUrl(),source.creativeType(),source.template(),source.mediaId(),source.placement(),source.device(),source.startsAt(),source.endsAt(),false,source.priority()));
  assertThat(ads.active(Placement.IN_FEED,Device.MOBILE)).noneMatch(a->a.id().equals(campaign.id()));
  assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(fresh.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(vn.covie.shared.web.ApiException.class);
  var enabled=ads.update(null,campaign.id(),VersionPrecondition.format(disabled.version()),source);
  var beforeDelete=ads.active(Placement.IN_FEED,Device.MOBILE).stream().filter(a->a.id().equals(campaign.id())).findFirst().orElseThrow();
  ads.delete(null,campaign.id(),VersionPrecondition.format(enabled.version()));
  assertThatThrownBy(()->ads.event(campaign.id(),new AdEventRequest(beforeDelete.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(vn.covie.shared.web.ApiException.class);
  assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?",Long.class,campaign.id())).isEqualTo(1);
 }
 @Test void deliveryRequiresElapsedVisibilityAndCannotBeReusedForAnotherCampaign() {
  var first=ads.create(null,request("/search",1000));var other=ads.create(null,request("/search",999));
  var delivery=ads.active(Placement.IN_FEED,Device.DESKTOP).stream().filter(a->a.id().equals(first.id())).findFirst().orElseThrow();
  var impression=new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.IMPRESSION,1000);
  assertThatThrownBy(()->ads.event(first.id(),impression)).isInstanceOf(vn.covie.shared.web.ApiException.class);
  when(clock.now()).thenReturn(now.plusSeconds(1));ads.event(first.id(),impression);
  assertThatThrownBy(()->ads.event(other.id(),new AdEventRequest(delivery.deliveryToken(),UUID.randomUUID(),AdEventRequest.Type.CLICK,0))).isInstanceOf(vn.covie.shared.web.ApiException.class);
  assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?",Long.class,first.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?",Long.class,other.id())).isZero();
 }
 @Test void paginatesBeyondOneHundredWithoutLosingOldCampaigns() {
  var oldest=ads.create(null,request("/search",1));
  for(int n=0;n<101;n++)ads.create(null,request("/search",1));
  var first=ads.list(null,0);var second=ads.list(null,100);
  assertThat(first).hasSize(100);assertThat(second).isNotEmpty();
  var firstIds=first.stream().map(AdCampaignResponse::id).toList();assertThat(second).noneMatch(a->firstIds.contains(a.id()));assertThat(second).anyMatch(a->a.id().equals(oldest.id()));
  ads.delete(null,oldest.id(),VersionPrecondition.format(oldest.version()));assertThat(ads.list(null,100)).noneMatch(a->a.id().equals(oldest.id()));
 }
}
