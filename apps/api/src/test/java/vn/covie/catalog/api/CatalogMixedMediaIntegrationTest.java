package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;
import vn.covie.media.model.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.*;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"covie.social-v2.video-enabled=true", "covie.social-v2.previews-enabled=true",
        "covie.social-v2.admin-reads-enabled=true", "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
class CatalogMixedMediaIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired MediaAssetRepository assets;
    @Autowired vn.covie.settings.persistence.SystemSettingRepository settings;
    private final ObjectMapper json = new ObjectMapper();
    private UUID owner;
    private UUID merchant;
    private UUID category;
    private UUID location;
    private String token;
    private String route;

    @BeforeEach void fixture() {
        owner = user();
        merchant = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant, "Mixed shop", "mixed-" + merchant, Instant.now()));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchant, owner, MerchantRole.OWNER, Instant.now()));
        category = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Mixed", "mixed-" + merchant,
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        location = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchant, "Mixed location", "1 CoVie", 10.8, 106.7, Instant.now())).getId();
        token = bearer(owner);
        route = "/api/v1/merchants/" + merchant + "/catalog-items";
    }

    @Test void createsReplaysAndReordersMixedGalleryAcrossSellerPublicPreviewAndAdminWithoutVideoBecomingCover() throws Exception {
        UUID first = media(owner, false, true), video = media(owner, true, true), second = media(owner, false, true);
        List<UUID> initial = List.of(first, video, second);
        String key = UUID.randomUUID().toString();
        String body = body(initial);
        var created = mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        String firstResponse = created.getContentAsString();
        mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(content().json(firstResponse));
        UUID id = UUID.fromString(json.readTree(firstResponse).path("id").asText());
        assertStored(id, initial, first);
        var seller = read(route + "/" + id, token);
        assertMedia(seller.path("media"), initial, video);
        String version = "\"v" + seller.path("version").asLong() + "\"";
        mvc.perform(post(route + "/" + id + "/publish").header("Authorization", token).header("If-Match", version))
                .andExpect(status().isNoContent());
        List<UUID> reordered = List.of(video, second, first);
        version = "\"v" + read(route + "/" + id, token).path("version").asLong() + "\"";
        mvc.perform(patch(route + "/" + id).header("Authorization", token).header("If-Match", version)
                .contentType("application/json").content(json.writeValueAsString(Map.of("mediaIds", reordered))))
                .andExpect(status().isNoContent());
        assertStored(id, reordered, second);
        assertMedia(read(route + "/" + id, token).path("media"), reordered, video);
        assertMedia(read("/api/v1/catalog/items/" + id + "?locationId=" + location, null).path("media"), reordered, video);
        var preview = json.readTree(mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds", id.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertMedia(preview.path(id.toString()).path("media"), reordered, video);
        assertThat(preview.path(id.toString()).path("media").get(0).path("isCover").asBoolean()).isFalse();
        assertThat(preview.path(id.toString()).path("media").get(1).path("isCover").asBoolean()).isTrue();
        UUID admin = user();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())", UUID.randomUUID(), admin);
        assertMedia(read("/api/v1/admin/catalog-items/" + id, bearer(admin)).path("media"), reordered, video);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='CATALOG_ITEM_CREATED'", Long.class, id)).isEqualTo(1);
        jdbc.update("update media_assets set deleted_at=now() where id=?", second);
        var fallback = json.readTree(mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds", id.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path(id.toString()).path("media");
        assertThat(fallback.size()).isEqualTo(2);
        assertThat(fallback.get(0).path("isCover").asBoolean()).isFalse();
        assertThat(fallback.get(1).path("isCover").asBoolean()).isTrue();
    }

    @Test void rejectsTwoVideosForeignPendingAndElevenDistinctAssetsWithoutPartialCreate() throws Exception {
        UUID video = media(owner, true, true);
        List<List<UUID>> invalid = new ArrayList<>();
        invalid.add(List.of(video, media(owner, true, true)));
        invalid.add(List.of(media(user(), true, true)));
        invalid.add(List.of(media(owner, true, false)));
        var eleven = new ArrayList<UUID>();
        for (int index = 0; index < 11; index++) eleven.add(media(owner, false, true));
        invalid.add(eleven);
        long before = jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?", Long.class, merchant);
        for (var mediaIds : invalid) {
            mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json").content(body(mediaIds))).andExpect(status().is4xxClientError());
            assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?", Long.class, merchant)).isEqualTo(before);
        }
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id=?", Long.class, owner)).isZero();
    }

    @Test void videoOnlyFlagStillRechecksPreApprovalAndRejectedReplacementRollsBack() throws Exception {
        UUID image = media(owner, false, true), video = media(owner, true, true);
        var created = mvc.perform(post(route).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json").content(body(List.of(image)))).andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post(route + "/" + id + "/publish").header("Authorization", token).header("If-Match", "\"v0\""))
                .andExpect(status().isNoContent());
        settings.saveAndFlush(vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(), "moderation.mode",
                vn.covie.settings.model.SettingValueType.ENUM, new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),
                merchant, owner, Instant.now()));
        String version = "\"v" + read(route + "/" + id, token).path("version").asLong() + "\"";
        mvc.perform(patch(route + "/" + id).header("Authorization", token).header("If-Match", version)
                .contentType("application/json").content(json.writeValueAsString(Map.of("mediaIds", List.of(image, video)))))
                .andExpect(status().isNoContent());
        assertThat(read(route + "/" + id, token).path("moderationStatus").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'", Long.class, id)).isEqualTo(1);
        mvc.perform(get("/api/v1/catalog/items/" + id)).andExpect(status().isNotFound());
        mvc.perform(head("/api/v1/media/" + video + "/content")).andExpect(status().isNotFound());
        mvc.perform(head("/api/v1/media/" + video + "/content").header("Authorization", token)).andExpect(status().isOk());
        var beforeItem = jdbc.queryForList("select to_jsonb(i)::text from catalog_items i where id=?", String.class, id);
        var beforeLinks = jdbc.queryForList("select to_jsonb(cm)::text from catalog_item_media cm where catalog_item_id=? order by sort_order", String.class, id);
        version = "\"v" + read(route + "/" + id, token).path("version").asLong() + "\"";
        mvc.perform(patch(route + "/" + id).header("Authorization", token).header("If-Match", version)
                .contentType("application/json").content(json.writeValueAsString(Map.of("mediaIds", List.of(image, video, media(owner, true, true))))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForList("select to_jsonb(i)::text from catalog_items i where id=?", String.class, id)).isEqualTo(beforeItem);
        assertThat(jdbc.queryForList("select to_jsonb(cm)::text from catalog_item_media cm where catalog_item_id=? order by sort_order", String.class, id)).isEqualTo(beforeLinks);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?", Long.class, id)).isEqualTo(1);
    }

    private void assertStored(UUID item, List<UUID> ids, UUID cover) {
        assertThat(jdbc.queryForList("select media_asset_id from catalog_item_media where catalog_item_id=? order by sort_order", UUID.class, item)).isEqualTo(ids);
        assertThat(jdbc.queryForList("select media_asset_id from catalog_item_media where catalog_item_id=? and is_primary", UUID.class, item)).containsExactly(cover);
        assertThat(jdbc.queryForObject("select media_type from catalog_item_media where catalog_item_id=? and media_asset_id=?", String.class, item,
                ids.stream().filter(id -> assets.findById(id).orElseThrow().getPurpose() == MediaPurpose.CATALOG_VIDEO).findFirst().orElseThrow())).isEqualTo("VIDEO");
    }

    private void assertMedia(JsonNode media, List<UUID> ids, UUID video) {
        var actual = new ArrayList<UUID>();
        media.forEach(entry -> actual.add(UUID.fromString(entry.path("id").asText())));
        assertThat(actual).isEqualTo(ids);
        for (var entry : media) if (entry.path("id").asText().equals(video.toString())) {
            assertThat(entry.path("url").asText()).isEqualTo("http://localhost:3000/api/v1/media/" + video + "/content");
            assertThat(entry.path("video").path("durationMillis").asLong()).isEqualTo(1000);
            assertThat(entry.has("storageKey")).isFalse();
        } else assertThat(entry.hasNonNull("video")).isFalse();
    }

    private JsonNode read(String path, String bearer) throws Exception {
        var request = get(path);
        if (bearer != null) request.header("Authorization", bearer);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String body(List<UUID> ids) throws Exception {
        return json.writeValueAsString(Map.of("type", "PRODUCT", "listingKind", "STANDARD", "categoryId", category,
                "name", "Mixed item", "description", "Commercial description", "mediaIds", ids,
                "locationOffers", List.of(Map.of("merchantLocationId", location, "variantClientKey", "__default__", "price", 25000,
                        "currency", "VND", "available", true, "inventoryMode", "UNTRACKED"))));
    }

    private UUID media(UUID actor, boolean video, boolean ready) {
        var asset = MediaAssetEntity.pending(UUID.randomUUID(), actor, "mixed/" + UUID.randomUUID(),
                video ? MediaPurpose.CATALOG_VIDEO : MediaPurpose.CATALOG_IMAGE, video ? "video/mp4" : "image/png", 24,
                Instant.now().plusSeconds(600), Instant.now());
        if (video && ready) asset.recordVideoMetadata(64, 48, 1000, "h264");
        if (ready) asset.markReady(Instant.now());
        return assets.saveAndFlush(asset).getId();
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(id, "Mixed owner", Instant.now()));
        return id;
    }

    private String bearer(UUID id) {
        return "Bearer " + issuer.issue(id, new DeviceMetadata("Test", "Mixed media")).accessToken().value();
    }
}
