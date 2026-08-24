package vn.covie.merchantcatalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.media.storage.StoredObjectMetadata;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class Plan03AcceptanceIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.now();
    private static final byte[] PNG = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @MockitoBean ObjectStorage storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        when(storage.presignPut(any(), any(), anyLong(), any())).thenReturn(new PresignedUpload(
                URI.create("https://uploads.example/signed"), "PUT", NOW.plusSeconds(600), Map.of("content-type", "image/png")));
    }

    @Test
    void sellerCanCreatePublishAndGuestCanDiscoverTheConcreteOffer() throws Exception {
        Actor seller = actor("seller", false);
        UUID merchantId = createMerchant(seller, "Bright Cafe");
        UUID locationId = createLocation(seller, merchantId, "Main", true);
        UUID categoryId = createCategory();
        UUID mediaId = readyCatalogMedia(seller.userId(), "catalog/hero.png");

        MvcResult created = mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId)
                        .with(seller.jwt()).header("Idempotency-Key", "plan03-acceptance-create")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "type":"PRODUCT",
                                  "listingKind":"STANDARD",
                                  "categoryId":"%s",
                                  "name":"Caramel Latte",
                                  "mediaIds":["%s"],
                                  "locationOffers":[
                                    {
                                      "merchantLocationId":"%s",
                                      "variantClientKey":"__default__",
                                      "price":25000,
                                      "currency":"VND",
                                      "available":true,
                                      "inventoryMode":"UNTRACKED"
                                    }
                                  ]
                                }
                                """.formatted(categoryId, mediaId, locationId)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID itemId = UUID.fromString(read(created).path("id").asText());
        UUID locationVariantId = offers.findAllActiveRecordsByCatalogItemId(itemId).getFirst().getId();

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish", merchantId, itemId)
                        .with(seller.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isNoContent());

        MvcResult discovery = mockMvc.perform(get("/api/v1/catalog/items")
                        .param("lat", "10.8")
                        .param("lng", "106.7")
                        .param("categoryId", categoryId.toString())
                        .param("sort", "PRICE_ASC")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode discovered = java.util.stream.StreamSupport.stream(read(discovery).path("page").path("items").spliterator(), false)
                .filter(candidate -> itemId.toString().equals(candidate.path("id").asText()))
                .findFirst()
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(discovered.path("name").asText()).isEqualTo("Caramel Latte");
        org.assertj.core.api.Assertions.assertThat(discovered.path("merchant").path("id").asText()).isEqualTo(merchantId.toString());
        org.assertj.core.api.Assertions.assertThat(discovered.path("primaryMedia").path("id").asText()).isEqualTo(mediaId.toString());
        org.assertj.core.api.Assertions.assertThat(discovered.path("resolvedLocation").path("id").asText()).isEqualTo(locationId.toString());
        org.assertj.core.api.Assertions.assertThat(discovered.path("priceFrom").path("amount").asInt()).isEqualTo(25000);

        mockMvc.perform(get("/api/v1/catalog/items/{itemId}", itemId)
                        .param("lat", "10.8")
                        .param("lng", "106.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Caramel Latte"))
                .andExpect(jsonPath("$.merchant.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.media[0].id").value(mediaId.toString()))
                .andExpect(jsonPath("$.resolvedLocation.id").value(locationId.toString()))
                .andExpect(jsonPath("$.variants[0].offer.locationVariantId").value(locationVariantId.toString()))
                .andExpect(jsonPath("$.variants[0].offer.price.amount").value(25000));
    }

    private UUID createMerchant(Actor actor, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/me/merchants").with(actor.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"Plan 03\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID createLocation(Actor actor, UUID merchantId, String name, boolean serviceEnabled) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/merchants/{merchantId}/locations", merchantId)
                        .with(actor.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "addressText":"1 CoVie Street",
                                  "latitude":10.8,
                                  "longitude":106.7,
                                  "pinSource":"GPS",
                                  "timezone":"Asia/Ho_Chi_Minh",
                                  "pickupEnabled":true,
                                  "sellerDeliveryEnabled":true,
                                  "serviceEnabled":%s,
                                  "minimumDeliveryOrder":10000,
                                  "currency":"VND"
                                }
                                """.formatted(name, serviceEnabled)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID createCategory() {
        return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food",
                "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
    }

    private UUID readyCatalogMedia(UUID ownerId, String key) {
        UUID mediaId = UUID.randomUUID();
        var asset = mediaAssets.saveAndFlush(vn.covie.media.model.MediaAssetEntity.pending(mediaId, ownerId, key,
                MediaPurpose.CATALOG_IMAGE, "image/png", PNG.length, NOW.plusSeconds(600), NOW));
        asset.markReady(NOW);
        mediaAssets.saveAndFlush(asset);
        asset = mediaAssets.findById(mediaId).orElseThrow();
        when(storage.head(asset.getStorageKey())).thenReturn(new StoredObjectMetadata("image/png", (long) PNG.length));
        when(storage.readPrefix(asset.getStorageKey(), 32)).thenReturn(PNG);
        return mediaId;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Actor actor(String prefix, boolean includeRolesClaim) {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, prefix, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId, includeRolesClaim);
    }

    private record Actor(UUID userId, UUID sessionId, boolean includeRolesClaim) {
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString())
                    .claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
