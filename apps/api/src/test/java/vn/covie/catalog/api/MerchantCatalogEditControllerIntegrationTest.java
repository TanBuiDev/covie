package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMemberLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@org.springframework.boot.test.context.SpringBootTest(properties={"covie.social-v2.caption-authoring-enabled=true","covie.social-v2.previews-enabled=true","covie.outbox.max-events-per-poll=0"})
class MerchantCatalogEditControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web; @Autowired UserRepository users; @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants; @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantMemberLocationRepository assignments; @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories; @Autowired CatalogItemRepository items; @Autowired VariantRepository variants;
    @Autowired CategoryAttributeRepository categoryAttributes;
    @Autowired LocationVariantRepository offers;
    @Autowired OptionGroupRepository optionGroups; @Autowired OptionValueRepository optionValues;
    @Autowired VariantOptionValueRepository variantOptions;
    @Autowired MediaAssetRepository mediaAssets; @Autowired CatalogItemMediaRepository itemMedia;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void issuedSellerReadsOnlyCurrentDecisionReasonWithinCatalogManagementScope() throws Exception {
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var issuer=web.getBean(vn.covie.auth.application.InitialSessionIssuer.class);
        Actor admin=actor();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin.userId());
        String administrator="Bearer "+issuer.issue(admin.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Seller reason admin")).accessToken().value();
        for (String action:List.of("HIDE","REJECT")) {
            Actor owner=actor(),manager=actor(),staff=actor(),outsider=actor();
            Fixture fixture=fixture(owner,MerchantRole.OWNER);
            memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),fixture.merchantId(),manager.userId(),MerchantRole.MANAGER,NOW));
            var staffMembership=memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),fixture.merchantId(),staff.userId(),MerchantRole.STAFF,NOW));
            assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(),staffMembership.getId(),offers.findById(fixture.offerId()).orElseThrow().getMerchantLocationId(),NOW));
            Fixture foreign=fixture(outsider,MerchantRole.OWNER);
            UUID caseId=UUID.randomUUID(),foreignCase=UUID.randomUUID();
            jdbc.update("update catalog_items set listing_status='ACTIVE',moderation_status=? where id=?",action.equals("HIDE")?"APPROVED":"PENDING",fixture.itemId());
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at) values (?,?,'CATALOG_ITEM',?,'OPEN_REASON_PRIVATE','OPEN',now())",caseId,action.equals("HIDE")?"SYSTEM_FLAG":"PRE_APPROVAL",fixture.itemId());
            mockMvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+(action.equals("HIDE")?"/hide-content":"/reject"))
                    .header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SPAM\",\"note\":\"ADMIN_NOTE_PRIVATE\"}"))
                    .andExpect(status().isOk());
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'FOREIGN_PRIVATE','RESOLVED',now(),now())",foreignCase,foreign.itemId());
            jdbc.update("insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,note,metadata,created_at) values (?,?,?,?,'FOREIGN_PRIVATE','FOREIGN_NOTE_PRIVATE','{}'::jsonb,now()+interval '1 hour')",UUID.randomUUID(),foreignCase,action,admin.userId());
            var tokens=new java.util.LinkedHashMap<UUID,String>();
            for (Actor actor:List.of(owner,manager,staff,outsider)) tokens.put(actor.userId(),"Bearer "+issuer.issue(actor.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Seller reason")).accessToken().value());
            var before=reasonReadSnapshot(jdbc);
            for (Actor permitted:List.of(owner,manager)) {
                String body=mockMvc.perform(get(itemPath(fixture)).header("Authorization",tokens.get(permitted.userId())))
                        .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                        .andExpect(jsonPath("$.moderationReasonCode").value("SPAM")).andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain("ADMIN_NOTE_PRIVATE","OPEN_REASON_PRIVATE","FOREIGN_PRIVATE","FOREIGN_NOTE_PRIVATE");
            }
            mockMvc.perform(get(itemPath(fixture)).header("Authorization",tokens.get(staff.userId())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.moderationReasonCode").doesNotExist());
            mockMvc.perform(get(itemPath(fixture)).header("Authorization",tokens.get(outsider.userId()))).andExpect(status().isNotFound());
            mockMvc.perform(get(itemPath(fixture))).andExpect(status().isUnauthorized());
            assertThat(reasonReadSnapshot(jdbc)).isEqualTo(before);
            mockMvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+"/restore-content")
                    .header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString()))
                    .andExpect(status().isOk());
            mockMvc.perform(get(itemPath(fixture)).header("Authorization",tokens.get(owner.userId())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.moderationReasonCode").doesNotExist());
            if (action.equals("HIDE")) {
                UUID nextCase=UUID.randomUUID();
                jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'OPEN_REASON_PRIVATE','OPEN',now())",nextCase,fixture.itemId());
                mockMvc.perform(post("/api/v1/admin/moderation-cases/"+nextCase+"/hide-content").header("Authorization",administrator)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"UPDATED_REASON\",\"note\":\"ADMIN_NOTE_PRIVATE\"}"))
                        .andExpect(status().isOk());
                mockMvc.perform(get(itemPath(fixture)).header("Authorization",tokens.get(owner.userId())))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.moderationReasonCode").value("UPDATED_REASON"));
            }
        }
    }

    private java.util.Map<String,List<String>> reasonReadSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        for (String table:List.of("catalog_items","variants","location_variants","catalog_item_media","moderation_cases","moderation_actions","audit_logs","outbox_events"))
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }

    @Test
    void captionPatchPreservesCommerceAndRechecksConfiguredModeration() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var item=items.findById(fixture.itemId()).orElseThrow();
        item.update(item.getPrimaryCategoryId(),item.getName(),"Commercial description",NOW);
        item.publish(NOW); item.requireModeration(NOW); item.approveModeration(NOW);
        items.saveAndFlush(item);
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        String bearer="Bearer "+web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(owner.userId(),new vn.covie.auth.model.DeviceMetadata("Caption","catalog-test")).accessToken().value();
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"  Social caption\\nsecond line  \"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v2\""));
        mockMvc.perform(get(itemPath(fixture)).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.socialCaption").value("Social caption\nsecond line"))
                .andExpect(jsonPath("$.description").value("Commercial description"));
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Caption item\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v3\""));
        assertThat(jdbc.queryForObject("select social_caption from catalog_items where id=?",String.class,fixture.itemId()))
                .isEqualTo("Social caption\nsecond line");
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v3\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":null}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v4\""));
        assertThat(jdbc.queryForObject("select social_caption from catalog_items where id=?",String.class,fixture.itemId())).isNull();
        var beforeInvalid=jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v4\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\""+"a".repeat(3001)+"\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Stale\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId())).isEqualTo(beforeInvalid);
        web.getBean(vn.covie.settings.persistence.SystemSettingRepository.class).saveAndFlush(
                vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",
                    vn.covie.settings.model.SettingValueType.ENUM,new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),
                    fixture.merchantId(),owner.userId(),NOW));
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v4\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Changed approved content\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v5\""));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getModerationStatus().name()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",Long.class,fixture.itemId())).isEqualTo(1);
        mockMvc.perform(get("/api/v1/catalog/items/"+fixture.itemId())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(offerBefore);
        assertThat(items.findById(fixture.itemId()).orElseThrow().getDescription()).isEqualTo("Commercial description");
    }

    @Test
    void restoreCannotPublishHiddenSocialEditsWithoutRequiredApproval() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var item=items.findById(fixture.itemId()).orElseThrow();
        item.publish(NOW); item.requireModeration(NOW); item.approveModeration(NOW);
        item.updateSocialCaption("Reviewed caption",NOW);
        items.saveAndFlush(item);
        var original=readyMedia(owner.userId()); var replacement=readyMedia(owner.userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item,original,0,true));
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        web.getBean(vn.covie.settings.persistence.SystemSettingRepository.class).saveAndFlush(
                vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",
                        vn.covie.settings.model.SettingValueType.ENUM,new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),
                        fixture.merchantId(),owner.userId(),NOW));
        Actor admin=actor();
        web.getBean(vn.covie.users.persistence.SystemRoleAssignmentRepository.class).saveAndFlush(
                vn.covie.users.model.SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.userId(),vn.covie.shared.security.SystemRole.ADMIN,NOW));
        var issuer=web.getBean(vn.covie.auth.application.InitialSessionIssuer.class);
        String seller="Bearer "+issuer.issue(owner.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Restore seller")).accessToken().value();
        String administrator="Bearer "+issuer.issue(admin.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Restore admin")).accessToken().value();
        UUID caseId=UUID.randomUUID();
        web.getBean(vn.covie.moderation.persistence.ModerationCaseRepository.class).saveAndFlush(
                vn.covie.moderation.model.ModerationCaseEntity.systemFlag(caseId,
                        new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM,fixture.itemId()),"SPAM","Restore fixture",NOW));
        String route="/api/v1/admin/moderation-cases/"+caseId;
        String preview="/api/v1/catalog/social-previews";
        String itemJson="$['"+fixture.itemId()+"']";
        mockMvc.perform(get(preview).param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson+".socialCaption").value("Reviewed caption"));
        mockMvc.perform(post(route+"/hide-content").header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SPAM\"}")).andExpect(status().isOk());
        var hidden=items.findById(fixture.itemId()).orElseThrow();
        assertThat(hidden.getModerationStatus().name()).isEqualTo("HIDDEN");
        mockMvc.perform(post(route+"/restore-content").header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get(preview).param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson+".socialCaption").value("Reviewed caption"));
        caseId=UUID.randomUUID();
        web.getBean(vn.covie.moderation.persistence.ModerationCaseRepository.class).saveAndFlush(
                vn.covie.moderation.model.ModerationCaseEntity.systemFlag(caseId,
                        new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM,fixture.itemId()),"SPAM","Second hide fixture",NOW));
        route="/api/v1/admin/moderation-cases/"+caseId;
        mockMvc.perform(post(route+"/hide-content").header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SPAM\"}")).andExpect(status().isOk());
        hidden=items.findById(fixture.itemId()).orElseThrow();
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",seller).header("If-Match","\"v"+hidden.getVersion()+"\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Unreviewed replacement caption\",\"mediaIds\":[\""+replacement.getId()+"\"]}"))
                .andExpect(status().isNoContent());
        assertThat(items.findById(fixture.itemId()).orElseThrow().getModerationStatus().name()).isEqualTo("PENDING");
        UUID approval=jdbc.queryForObject("select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",UUID.class,fixture.itemId());
        mockMvc.perform(get(preview).param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson).doesNotExist());
        var pendingRow=jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId());
        String restoreKey=UUID.randomUUID().toString();
        for (int replay=0;replay<2;replay++) mockMvc.perform(post(route+"/restore-content").header("Authorization",administrator).header("Idempotency-Key",restoreKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"RECHECK\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MODERATION_TARGET_STATE_CONFLICT"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId())).isEqualTo(pendingRow);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=? and action_type='RESTORE'",Long.class,caseId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MODERATION_RESTORE'",Long.class,caseId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and payload->>'action'='RESTORE'",Long.class,caseId)).isZero();
        mockMvc.perform(get(preview).param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson).doesNotExist());
        mockMvc.perform(get("/api/v1/catalog/items/"+fixture.itemId())).andExpect(status().isNotFound());
        String approvalKey=UUID.randomUUID().toString();
        for (int replay=0;replay<2;replay++) mockMvc.perform(post("/api/v1/admin/moderation-cases/"+approval+"/approve")
                .header("Authorization",administrator).header("Idempotency-Key",approvalKey)).andExpect(status().isOk());
        mockMvc.perform(get(preview).param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson+".socialCaption").value("Unreviewed replacement caption"))
                .andExpect(jsonPath(itemJson+".media[0].id").value(replacement.getId().toString()));
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=? and action_type='APPROVE'",Long.class,approval)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(offerBefore);
    }

    @Test
    void restoreCannotPublishRejectedSocialEditsWithoutRequiredApproval() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var item=items.findById(fixture.itemId()).orElseThrow();
        item.publish(NOW); item.requireModeration(NOW); item.updateSocialCaption("Rejected caption",NOW);
        items.saveAndFlush(item);
        var original=readyMedia(owner.userId()); var replacement=readyMedia(owner.userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item,original,0,true));
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        web.getBean(vn.covie.settings.persistence.SystemSettingRepository.class).saveAndFlush(
                vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",
                        vn.covie.settings.model.SettingValueType.ENUM,new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),
                        fixture.merchantId(),owner.userId(),NOW));
        Actor admin=actor();
        web.getBean(vn.covie.users.persistence.SystemRoleAssignmentRepository.class).saveAndFlush(
                vn.covie.users.model.SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.userId(),vn.covie.shared.security.SystemRole.ADMIN,NOW));
        var issuer=web.getBean(vn.covie.auth.application.InitialSessionIssuer.class);
        String seller="Bearer "+issuer.issue(owner.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Rejected seller")).accessToken().value();
        String administrator="Bearer "+issuer.issue(admin.userId(),new vn.covie.auth.model.DeviceMetadata("Test","Rejected admin")).accessToken().value();
        UUID caseId=UUID.randomUUID();
        web.getBean(vn.covie.moderation.persistence.ModerationCaseRepository.class).saveAndFlush(
                vn.covie.moderation.model.ModerationCaseEntity.preApprovalCatalog(caseId,fixture.itemId(),NOW));
        String route="/api/v1/admin/moderation-cases/"+caseId;
        mockMvc.perform(post(route+"/reject").header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SPAM\"}")).andExpect(status().isOk());
        var rejected=items.findById(fixture.itemId()).orElseThrow();
        assertThat(rejected.getModerationStatus().name()).isEqualTo("REJECTED");
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",seller).header("If-Match","\"v"+rejected.getVersion()+"\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"New rejected caption\",\"mediaIds\":[\""+replacement.getId()+"\"]}"))
                .andExpect(status().isNoContent());
        var restore=mockMvc.perform(post(route+"/restore-content").header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andReturn().getResponse();
        assertThat(restore.getStatus()).as(restore.getContentAsString()).isIn(200,409);
        String itemJson="$['"+fixture.itemId()+"']";
        mockMvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson).doesNotExist());
        assertThat(restore.getStatus()).isEqualTo(409);
        assertThat(items.findById(fixture.itemId()).orElseThrow().getModerationStatus().name()).isEqualTo("PENDING");
        UUID approval=jdbc.queryForObject("select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",UUID.class,fixture.itemId());
        mockMvc.perform(post("/api/v1/admin/moderation-cases/"+approval+"/approve").header("Authorization",administrator)
                .header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",fixture.itemId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath(itemJson+".socialCaption").value("New rejected caption"))
                .andExpect(jsonPath(itemJson+".media[0].id").value(replacement.getId().toString()));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(offerBefore);
    }

    @Test
    void pausedCaptionNeedsApprovalWithoutAutomaticallyResumingTheListing() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var item=items.findById(fixture.itemId()).orElseThrow();
        item.publish(NOW); item.requireModeration(NOW); item.approveModeration(NOW); item.pause(NOW);
        items.saveAndFlush(item);
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        String bearer="Bearer "+web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(owner.userId(),new vn.covie.auth.model.DeviceMetadata("Paused caption","catalog-test")).accessToken().value();
        web.getBean(vn.covie.settings.persistence.SystemSettingRepository.class).saveAndFlush(
                vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",
                        vn.covie.settings.model.SettingValueType.ENUM,new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),
                        fixture.merchantId(),owner.userId(),NOW));
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Changed while paused\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v2\""));
        var edited=items.findById(fixture.itemId()).orElseThrow();
        assertThat(edited.getModerationStatus().name()).isEqualTo("PENDING");
        assertThat(edited.getListingStatus().name()).isEqualTo("PAUSED");
        mockMvc.perform(get(itemPath(fixture)).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ACTIVATE"))));
        mockMvc.perform(post(itemPath(fixture)+"/activate").header("Authorization",bearer).header("If-Match","\"v2\""))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/catalog/items/"+fixture.itemId())).andExpect(status().isNotFound());
        UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",
                UUID.class,fixture.itemId());
        Actor admin=actor();
        web.getBean(vn.covie.users.persistence.SystemRoleAssignmentRepository.class).saveAndFlush(
                vn.covie.users.model.SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.userId(),vn.covie.shared.security.SystemRole.ADMIN,NOW));
        String adminBearer="Bearer "+web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(admin.userId(),new vn.covie.auth.model.DeviceMetadata("Paused approval","catalog-test")).accessToken().value();
        String key=UUID.randomUUID().toString();
        for (int replay=0;replay<2;replay++) {
            mockMvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+"/approve").header("Authorization",adminBearer)
                    .header("Idempotency-Key",key)).andExpect(status().isOk());
        }
        var approved=items.findById(fixture.itemId()).orElseThrow();
        assertThat(approved.getModerationStatus().name()).isEqualTo("APPROVED");
        assertThat(approved.getListingStatus().name()).isEqualTo("PAUSED");
        mockMvc.perform(get("/api/v1/catalog/items/"+fixture.itemId())).andExpect(status().isNotFound());
        mockMvc.perform(post(itemPath(fixture)+"/activate").header("Authorization",bearer).header("If-Match","\"v3\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v4\""));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getListingStatus().name()).isEqualTo("ACTIVE");
        mockMvc.perform(get("/api/v1/catalog/items/"+fixture.itemId())).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(offerBefore);
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MODERATION_APPROVE'",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",Long.class,caseId)).isEqualTo(1);
    }

    @Test
    void issuedCaptionJwtRespectsMerchantScopeStaffCapabilityAndMembershipRevocation() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        Actor manager=actor();
        var membership=memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),fixture.merchantId(),
                manager.userId(),MerchantRole.MANAGER,NOW));
        var issuer=web.getBean(vn.covie.auth.application.InitialSessionIssuer.class);
        String managerBearer="Bearer "+issuer.issue(manager.userId(),new vn.covie.auth.model.DeviceMetadata("Caption manager","catalog-test")).accessToken().value();
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",managerBearer).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Manager caption\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v1\""));
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var before=jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId());
        assertThat(before.get("social_caption")).isEqualTo("Manager caption");
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        var variantBefore=jdbc.queryForMap("select * from variants where id=?",fixture.variantId());
        var mediaBefore=jdbc.queryForList("select * from catalog_item_media where catalog_item_id=?",fixture.itemId());
        Actor otherOwner=actor(); Fixture otherMerchant=fixture(otherOwner,MerchantRole.OWNER);
        String outsiderBearer="Bearer "+issuer.issue(otherOwner.userId(),new vn.covie.auth.model.DeviceMetadata("Other caption merchant","catalog-test")).accessToken().value();
        for (String route : List.of(itemPath(fixture),"/api/v1/merchants/"+otherMerchant.merchantId()+"/catalog-items/"+fixture.itemId())) {
            mockMvc.perform(patch(route).header("Authorization",outsiderBearer).header("If-Match","\"v1\"")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Foreign write\"}"))
                    .andExpect(status().isNotFound());
            assertThat(jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId())).isEqualTo(before);
        }
        Actor staff=actor();
        var staffMembership=memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),fixture.merchantId(),
                staff.userId(),MerchantRole.STAFF,NOW));
        assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(),staffMembership.getId(),
                offers.findById(fixture.offerId()).orElseThrow().getMerchantLocationId(),NOW));
        String staffBearer="Bearer "+issuer.issue(staff.userId(),new vn.covie.auth.model.DeviceMetadata("Caption staff","catalog-test")).accessToken().value();
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",staffBearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Staff write\"}"))
                .andExpect(status().isForbidden());
        membership.revoke(); memberships.saveAndFlush(membership);
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",managerBearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Revoked write\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId())).isEqualTo(before);
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(offerBefore);
        assertThat(jdbc.queryForMap("select * from variants where id=?",fixture.variantId())).isEqualTo(variantBefore);
        assertThat(jdbc.queryForList("select * from catalog_item_media where catalog_item_id=?",fixture.itemId())).isEqualTo(mediaBefore);
    }

    @Test
    void v2MediaEditPreservesUnchangedLegacyGalleryAndEnforcesNewOrderingLimit() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var mediaIds=new java.util.ArrayList<UUID>();
        for (int index=0;index<12;index++) {
            var asset=readyMedia(owner.userId()); mediaIds.add(asset.getId());
            itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(),asset,index*2,index==0));
        }
        var jdbc=web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var beforeMedia=jdbc.queryForList("select * from catalog_item_media where catalog_item_id=? order by sort_order",fixture.itemId());
        var beforeOffer=jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId());
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String bearer="Bearer "+web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(owner.userId(),new vn.covie.auth.model.DeviceMetadata("Legacy media","catalog-test")).accessToken().value();
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"socialCaption\":\"Caption only\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("select * from catalog_item_media where catalog_item_id=? order by sort_order",fixture.itemId())).isEqualTo(beforeMedia);
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v1\"")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("name","Legacy item","mediaIds",mediaIds))))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("select * from catalog_item_media where catalog_item_id=? order by sort_order",fixture.itemId())).isEqualTo(beforeMedia);
        var beforeRejected=jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v2\"")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("mediaIds",mediaIds.subList(0,11)))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",fixture.itemId())).isEqualTo(beforeRejected);
        assertThat(jdbc.queryForList("select * from catalog_item_media where catalog_item_id=? order by sort_order",fixture.itemId())).isEqualTo(beforeMedia);
        var ordered=new java.util.ArrayList<>(mediaIds.subList(0,10)); java.util.Collections.reverse(ordered);
        mockMvc.perform(patch(itemPath(fixture)).header("Authorization",bearer).header("If-Match","\"v2\"")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("mediaIds",ordered))))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v3\""));
        assertThat(jdbc.queryForList("select media_asset_id from catalog_item_media where catalog_item_id=? order by sort_order",UUID.class,fixture.itemId())).isEqualTo(ordered);
        assertThat(jdbc.queryForObject("select media_asset_id from catalog_item_media where catalog_item_id=? and is_primary",UUID.class,fixture.itemId())).isEqualTo(ordered.getFirst());
        mockMvc.perform(get(itemPath(fixture)).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(10)).andExpect(jsonPath("$.media[0].id").value(ordered.getFirst().toString()));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",fixture.offerId())).isEqualTo(beforeOffer);
    }

    @Test
    void editsEachResourceAgainstItsOwnEtag() throws Exception {
        Actor owner = actor(); Fixture fixture = fixtureWithOptions(owner);
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Edited coffee\",\"tags\":[\"arabica\"]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(patch(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"COFFEE-01\",\"variantName\":\"Regular\",\"variantStatus\":\"PAUSED\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":30000,\"available\":false,\"inventoryMode\":\"TRACKED\",\"preparationTimeMinutes\":10}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getName()).isEqualTo("Edited coffee");
        assertThat(variants.findById(fixture.variantId()).orElseThrow().getVariantStatus().name()).isEqualTo("PAUSED");
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPrice()).isEqualTo(30000);
    }

    @Test
    void managerCanAppendOwnMediaWithoutReowningTheOwnersExistingMedia() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        Actor manager = actor();
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), fixture.merchantId(),
                manager.userId(), MerchantRole.MANAGER, NOW));
        MediaAssetEntity original = readyMedia(owner.userId());
        MediaAssetEntity addition = readyMedia(manager.userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(), original, 0, true));

        mockMvc.perform(patch(itemPath(fixture)).with(manager.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + original.getId() + "\",\"" + addition.getId() + "\"]}"))
                .andExpect(status().isNoContent());

        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association -> association.getId().getMediaAssetId())
                .containsExactly(original.getId(), addition.getId());
    }

    @Test
    void keepsStructuralEditingForbiddenToStaffButAllowsTheirAssignedOffer() throws Exception {
        Actor staff = actor(); Fixture fixture = fixture(staff, MerchantRole.STAFF);
        String bearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class).issue(staff.userId(), new vn.covie.auth.model.DeviceMetadata("Staff edit", "catalog-test")).accessToken().value();
        var jdbc = web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var beforeItem = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(beforeItem);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":31000}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        UUID unassignedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Unassigned", "2 CoVie", 10.8, 106.7, NOW)).getId();
        UUID unassignedOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), unassignedLocation,
                fixture.variantId(), 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        var beforeOffer = jdbc.queryForMap("select * from location_variants where id=?", unassignedOffer);
        mockMvc.perform(patch(itemPath(fixture) + "/location-offers/" + unassignedOffer).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":32000}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?", unassignedOffer)).isEqualTo(beforeOffer);
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPrice()).isEqualTo(31000);
        var assignedAfterEdit = jdbc.queryForMap("select * from location_variants where id=?", fixture.offerId());
        int removed = jdbc.update("delete from merchant_member_locations where membership_id in "
                + "(select id from merchant_memberships where merchant_profile_id=? and user_id=?)", fixture.merchantId(), staff.userId());
        assertThat(removed).isEqualTo(1);
        mockMvc.perform(patch(offerPath(fixture)).header("Authorization", bearer).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":33000}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?", fixture.offerId())).isEqualTo(assignedAfterEdit);
    }

    @Test
    void rejectsStaleVersionAndProtectsTheFinalVariant() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v7\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":31000}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        mockMvc.perform(delete(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_VARIANT_COMBINATION_INVALID"));
    }

    @Test
    void revalidatesRequiredAttributesWhenChangingThePrimaryCategory() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        String bearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(owner.userId(), new vn.covie.auth.model.DeviceMetadata("Catalog edit", "catalog-edit-test")).accessToken().value();
        UUID requiredCategory = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Required", "required-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 1)).getId();
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), requiredCategory, "Brand", "brand",
                CategoryAttributeType.TEXT, true, false, 0));
        var jdbc = web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var original = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primaryCategoryId\":\"" + requiredCategory + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_ATTRIBUTE_INVALID"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(original);
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primaryCategoryId\":\"" + requiredCategory
                                + "\",\"attributes\":[{\"attributeKey\":\"brand\",\"valueText\":\"CoVie\"}]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, fixture.itemId())).isEqualTo(requiredCategory);
        var stored = jdbc.queryForList("select * from catalog_item_attribute_values where catalog_item_id=?", fixture.itemId());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().get("value_text")).isEqualTo("CoVie");
        Actor outsider = actor();
        String foreignBearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(outsider.userId(), new vn.covie.auth.model.DeviceMetadata("Other catalog user", "catalog-test")).accessToken().value();
        var updated = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", foreignBearer).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attributes\":[{\"attributeKey\":\"brand\",\"valueText\":\"Rejected\"}]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(updated);
        assertThat(jdbc.queryForList("select * from catalog_item_attribute_values where catalog_item_id=?", fixture.itemId())).isEqualTo(stored);
    }

    @Test
    void distinguishesAnOmittedPreparationTimeFromAnExplicitNull() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), offer.isAvailable(), offer.getInventoryMode(), 15, NOW);
        offers.saveAndFlush(offer);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":26000}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v2\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isEqualTo(15);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v2\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"preparationTimeMinutes\":null}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v3\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isNull();
    }

    @Test
    void ignoresInjectedPreparationTimePresenceMarker() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), offer.isAvailable(), offer.getInventoryMode(), 15, NOW);
        offers.saveAndFlush(offer);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":26000,\"preparationTimeMinutesPresent\":true}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v2\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isEqualTo(15);
    }

    @Test
    void createsANewVariantAndArchivesTheOldOneInsteadOfChangingCombinations() throws Exception {
        Actor owner = actor(); Fixture fixture = fixtureWithOptions(owner);
        String path = "/api/v1/merchants/" + fixture.merchantId() + "/catalog-items/" + fixture.itemId() + "/variants";
        mockMvc.perform(post(path).with(owner.jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantName\":\"Replacement\",\"optionValueIds\":[\"" + fixture.replacementOptionId() + "\"]}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(header().string("ETag", "\"v0\""));
        mockMvc.perform(delete(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(variants.countByCatalogItemIdAndDeletedAtIsNull(fixture.itemId())).isEqualTo(1);
    }

    @Test
    void inventoryListDoesNotExposeUnassignedLocationsToStaff() throws Exception {
        Actor staff = actor();
        Fixture fixture = fixture(staff, MerchantRole.STAFF);
        UUID otherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Other", "2 CoVie", 10.8, 106.7, NOW)).getId();
        UUID otherOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        String path = "/api/v1/merchants/" + fixture.merchantId() + "/inventory";
        mockMvc.perform(get(path).with(staff.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].locationVariantId").value(fixture.offerId().toString()));
        Actor manager = actor();
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), fixture.merchantId(),
                manager.userId(), MerchantRole.MANAGER, NOW));
        mockMvc.perform(get(path).with(manager.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].locationVariantId").value(org.hamcrest.Matchers.hasItem(otherOffer.toString())));
    }

    @Test
    void failedMediaReplacementRollsBackEarlierAttachmentsAndItemEdits() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var original=readyMedia(owner.userId()); var addition=readyMedia(owner.userId()); var foreign=readyMedia(actor().userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(),original,0,true));
        mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt()).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rejected edit\",\"mediaIds\":[\""+original.getId()+"\",\""+addition.getId()+"\",\""+foreign.getId()+"\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getVersion()).isZero();
        assertThat(items.findById(fixture.itemId()).orElseThrow().getName()).isEqualTo("Coffee");
        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association->association.getId().getMediaAssetId()).containsExactly(original.getId());
        var pending=mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(),owner.userId(),"catalog/"+UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE,"image/png",8,NOW.plusSeconds(600),NOW));
        var wrongPurpose=MediaAssetEntity.pending(UUID.randomUUID(),owner.userId(),"avatar/"+UUID.randomUUID(),
                MediaPurpose.USER_AVATAR,"image/png",8,NOW.plusSeconds(600),NOW);
        wrongPurpose.markReady(NOW); wrongPurpose=mediaAssets.saveAndFlush(wrongPurpose);
        for(var rejected:java.util.List.of(pending,wrongPurpose)) mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt())
                .header("If-Match","\"v0\"").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[\""+original.getId()+"\",\""+rejected.getId()+"\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MEDIA_NOT_ATTACHABLE"));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getVersion()).isZero();
        mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt()).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mediaIds\":[\""+original.getId()+"\",\""+addition.getId()+"\",\""+addition.getId()+"\"]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v1\""));
        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association->association.getId().getMediaAssetId()).containsExactly(original.getId(),addition.getId());
    }

    @Test
    void bulkStockKeepsSuccessfulItemWhenAnotherOfferIsUntracked() throws Exception {
        Actor owner = actor();
        Fixture fixture = fixture(owner, MerchantRole.OWNER);
        UUID trackedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Tracked", "3 CoVie", 10.8, 106.7, NOW)).getId();
        UUID trackedId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), trackedLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(trackedId, NOW));
        UUID reservedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Reserved", "4 CoVie", 10.8, 106.7, NOW)).getId();
        UUID reservedId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), reservedLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        var reservedBalance = vn.covie.inventory.model.InventoryBalanceEntity.zero(reservedId, NOW);
        reservedBalance.adjust(10, 7, NOW);
        inventoryBalances.saveAndFlush(reservedBalance);
        String body = "{\"items\":[{\"locationVariantId\":\"" + trackedId + "\",\"stockOnHand\":5},"
                + "{\"locationVariantId\":\"" + fixture.offerId() + "\",\"stockOnHand\":7},"
                + "{\"locationVariantId\":\"" + reservedId + "\",\"stockOnHand\":5}],\"reasonCode\":\"AUDIT_TEST\"}";
        String key = UUID.randomUUID().toString();
        long movementsBefore = inventoryMovements.count();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/merchants/" + fixture.merchantId() + "/inventory/bulk-set-stock")
                            .with(owner.jwt()).header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].success").value(true))
                    .andExpect(jsonPath("$.results[0].stockOnHand").value(5))
                    .andExpect(jsonPath("$.results[1].success").value(false))
                    .andExpect(jsonPath("$.results[1].errorCode").value("INVENTORY_NOT_TRACKED"))
                    .andExpect(jsonPath("$.results[2].success").value(false))
                    .andExpect(jsonPath("$.results[2].errorCode").value("INVENTORY_BELOW_RESERVED_QUANTITY"));
        }
        assertThat(inventoryBalances.findById(trackedId).orElseThrow().getStockOnHand()).isEqualTo(5);
        assertThat(inventoryMovements.count()).isEqualTo(movementsBefore + 1);
        var unchanged = inventoryBalances.findById(reservedId).orElseThrow();
        assertThat(unchanged.getStockOnHand()).isEqualTo(10);
        assertThat(unchanged.getReservedQuantity()).isEqualTo(7);
    }

    @Test
    void staffCannotAdjustStockAtAnUnassignedLocation() throws Exception {
        Actor staff = actor();
        Fixture fixture = fixture(staff, MerchantRole.STAFF);
        UUID otherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Private", "5 CoVie", 10.8, 106.7, NOW)).getId();
        UUID otherOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(otherOffer, NOW));
        long before = inventoryMovements.count();
        for (String operation : List.of("set-stock", "stock-in", "stock-out")) {
            String body = operation.equals("set-stock") ? "{\"stockOnHand\":5}" : "{\"quantity\":5}";
            mockMvc.perform(post("/api/v1/merchants/" + fixture.merchantId() + "/inventory/" + otherOffer + "/" + operation)
                            .with(staff.jwt()).header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        }
        assertThat(inventoryBalances.findById(otherOffer).orElseThrow().getStockOnHand()).isZero();
        assertThat(inventoryMovements.count()).isEqualTo(before);
    }

    @Autowired vn.covie.inventory.persistence.InventoryBalanceRepository inventoryBalances;
    @Autowired vn.covie.inventory.persistence.InventoryMovementRepository inventoryMovements;

    @Test
    void singleStockCommandsReplayWithoutDuplicateLedgerAndRejectExcessWithdrawal() throws Exception {
        Actor owner = actor();
        Fixture fixture = fixture(owner, MerchantRole.OWNER);
        var offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), true, InventoryMode.TRACKED, null, NOW);
        offers.saveAndFlush(offer);
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(fixture.offerId(), NOW));
        long before = inventoryMovements.count();
        String prefix = "/api/v1/merchants/" + fixture.merchantId() + "/inventory/" + fixture.offerId();
        String[] operations = {"set-stock", "stock-in", "stock-out"};
        String[] bodies = {"{\"stockOnHand\":5}", "{\"quantity\":3}", "{\"quantity\":2}"};
        int[] expected = {5, 8, 6};
        for (int index = 0; index < operations.length; index++) {
            String key = UUID.randomUUID().toString();
            for (int attempt = 0; attempt < 2; attempt++) {
                mockMvc.perform(post(prefix + "/" + operations[index]).with(owner.jwt())
                                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(bodies[index]))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stockOnHand").value(expected[index]));
            }
        }
        mockMvc.perform(post(prefix + "/stock-out").with(owner.jwt())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(inventoryBalances.findById(fixture.offerId()).orElseThrow().getStockOnHand()).isEqualTo(6);
        assertThat(inventoryMovements.count()).isEqualTo(before + 3);
    }

    private Fixture fixture(Actor actor, MerchantRole role) {
        UUID merchantId = UUID.randomUUID(); merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "m-" + merchantId, NOW));
        MerchantMembershipEntity membership = memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, actor.userId(), role, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        if (role == MerchantRole.STAFF) assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), membership.getId(), locationId, NOW));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID(); items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = UUID.randomUUID(); variants.saveAndFlush(VariantEntity.createDefault(variantId, itemId, "Default", NOW));
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        return new Fixture(merchantId, itemId, variantId, offerId, null);
    }
    private Fixture fixtureWithOptions(Actor actor) {
        Fixture base = fixture(actor, MerchantRole.OWNER);
        OptionGroupEntity group = optionGroups.saveAndFlush(OptionGroupEntity.create(UUID.randomUUID(), base.itemId(), "Size", 0));
        OptionValueEntity small = optionValues.saveAndFlush(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "Small", 0));
        OptionValueEntity large = optionValues.saveAndFlush(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "Large", 1));
        VariantEntity old = variants.findById(base.variantId()).orElseThrow();
        variantOptions.saveAndFlush(VariantOptionValueEntity.associate(old, small));
        return new Fixture(base.merchantId(), base.itemId(), base.variantId(), base.offerId(), large.getId());
    }
    private static String itemPath(Fixture fixture) { return "/api/v1/merchants/" + fixture.merchantId() + "/catalog-items/" + fixture.itemId(); }
    private static String variantPath(Fixture fixture) { return itemPath(fixture) + "/variants/" + fixture.variantId(); }
    private static String offerPath(Fixture fixture) { return itemPath(fixture) + "/location-offers/" + fixture.offerId(); }
    private Actor actor() { UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now(); users.saveAndFlush(UserEntity.active(userId, "User", now)); sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS))); return new Actor(userId, sessionId); }
    private MediaAssetEntity readyMedia(UUID ownerId) {
        MediaAssetEntity asset = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "catalog/" + UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(600), NOW);
        asset.markReady(NOW.plusSeconds(1));
        return mediaAssets.saveAndFlush(asset);
    }
    private record Fixture(UUID merchantId, UUID itemId, UUID variantId, UUID offerId, UUID replacementOptionId) {}
    private record Actor(UUID userId, UUID sessionId) { SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() { return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString()).claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER")); } }
}

