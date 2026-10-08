package vn.covie.needs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.model.PinSource;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;
import org.springframework.http.MediaType;

class NeedEligibleItemsControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired NeedRequestRepository needs;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresTheSelectedLocationQueryParameter() throws Exception {
        Actor seller = actor();
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID()).with(seller.jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousCandidateReads() throws Exception {
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID())
                        .queryParam("locationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hidesForeignMerchantAndLocationBehindNotFound() throws Exception {
        Actor seller = actor();
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID())
                        .queryParam("locationId", UUID.randomUUID().toString()).with(seller.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesAnExistingMerchantNeedFromANonMember() throws Exception {
        Fixture fixture = visibleFixture();
        Actor stranger = actor();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(stranger.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesAnExistingMerchantNeedAfterMembershipRevocation() throws Exception {
        Fixture fixture = visibleFixture();
        var membership = memberships.findActive(fixture.merchantId(), fixture.seller().userId()).orElseThrow();
        membership.revoke();
        memberships.saveAndFlush(membership);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesModerationPendingNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.requireModeration(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesModerationRejectedNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.rejectModeration(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesCancelledNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.cancel(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesExpiredNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.expire(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesNeedNotDiscoverableAtTheSelectedLocation() throws Exception {
        Fixture fixture = visibleFixture();
        UUID otherLocationId = locations.saveAndFlush(location(fixture.merchantId(), "Điểm khác", true)).getId();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", otherLocationId.toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesPausedSelectedLocationBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        jdbc.update("update merchant_locations set location_status='PAUSED' where id=?", fixture.locationId());
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAResponseCommandWhenItsAssignedLocationIsPaused() throws Exception {
        Fixture fixture = visibleFixture();
        jdbc.update("update merchant_locations set location_status='PAUSED' where id=?", fixture.locationId());
        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsAResponseCommandForAModerationHiddenNeed() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.hide(Instant.now());
        needs.saveAndFlush(need);
        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsAnExplicitAuthorizedPageBeforeCandidateFiltering() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.maxResponseItems").isNumber());
    }

    @Test
    void returnsAProductOfferValidatedByTheResponsePolicy() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.items[0].catalogItemId").value(fixture.itemId().toString()))
                .andExpect(jsonPath("$.items[0].variantId").value(fixture.variantId().toString()))
                .andExpect(jsonPath("$.items[0].catalogItemName").value("Cà phê"))
                .andExpect(jsonPath("$.items[0].variantName").value("Mặc định"))
                .andExpect(jsonPath("$.items[0].currentPrice.amount").value(45000))
                .andExpect(jsonPath("$.items[0].available").value(true));
    }

    @Test
    void returnsOnlyAServiceOfferWithinItsPostgisServiceArea() throws Exception {
        Fixture fixture = visibleFixture(NeedType.SERVICE);
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("inventoryMode").doesNotContain("geoPoint");
    }

    @Test
    void returnsTheAuthenticatedMerchantsExistingResponseWithReadableItemNames() throws Exception {
        Fixture fixture = visibleFixture();
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString()),
                "note", "Có thể giao trong chiều nay."));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests/{needId}/response",
                        fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(fixture.merchantId().toString()))
                .andExpect(jsonPath("$.merchantLocationId").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.note").value("Có thể giao trong chiều nay."))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].catalogItemName").value("Cà phê"));
    }

    @Test
    void omitsAnOutOfStockTrackedOfferWhileKeepingTheUsableOffer() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID trackedVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-TRACKED", "Có theo dõi kho", false, 1, now)).getId();
        UUID trackedOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), trackedVariantId, 69000, "VND", true,
                InventoryMode.TRACKED, null, now)).getId();
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(trackedOfferId.toString());
    }

    @Test
    void omitsAnOfferFromAnUnrelatedCategory() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherCategoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null,
                "Danh mục khác", "other-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT,
                CategoryStatus.ACTIVE, 1)).getId();
        UUID otherItemId = UUID.randomUUID();
        CatalogItemEntity otherItem = CatalogItemEntity.create(otherItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, otherCategoryId,
                "Nội dung khác", "other-item-" + otherItemId, null, now);
        otherItem.publish(now);
        items.saveAndFlush(otherItem);
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(),
                otherItemId, "OTHER", "Mặc định", true, 0, now)).getId();
        UUID otherOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), otherVariantId, 69000, "VND", true,
                InventoryMode.UNTRACKED, null, now)).getId();
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(otherOfferId.toString());
    }

    @Test
    void pagesTwoEligibleOffersWithAContextBoundCursor() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-2", "Gói lớn", false, 1, now)).getId();
        UUID otherOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now)).getId();

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).queryParam("limit", "1")
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        var first = new ObjectMapper().readTree(firstBody);
        String cursor = first.path("nextCursor").asText();
        String firstOffer = first.path("items").get(0).path("locationVariantId").asText();
        String secondBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        String secondOffer = new ObjectMapper().readTree(secondBody)
                .path("items").get(0).path("locationVariantId").asText();
        assertThat(List.of(firstOffer, secondOffer)).containsExactlyInAnyOrder(
                fixture.offerId().toString(), otherOfferId.toString());
    }

    @Test
    void rejectsACursorReplayedAgainstAnotherVisibleNeed() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-2", "Gói lớn", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        NeedRequestEntity firstNeed = needs.findById(fixture.needId()).orElseThrow();
        UUID secondNeedId = needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(),
                firstNeed.getBuyerUserId(), NeedType.PRODUCT, firstNeed.getCategoryId(),
                "Cần thêm cà phê", "Cà phê", null, 1L, null,
                point(), new ObjectMapper().createObjectNode(), 5000,
                now.plus(1, ChronoUnit.DAYS), now)).getId();
        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        mockMvc.perform(get(path(), fixture.merchantId(), secondNeedId)
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("cursor", cursor).with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsACursorReplayedAgainstAnotherVisibleLocation() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-OTHER", "Gói khác", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        UUID otherLocationId = locations.saveAndFlush(location(fixture.merchantId(), "Điểm thứ hai", true)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocationId,
                fixture.variantId(), 45000, "VND", true, InventoryMode.UNTRACKED, null, now));
        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", otherLocationId.toString())
                        .queryParam("cursor", cursor).with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMalformedCandidateCursor() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("cursor", "not-a-candidate-cursor")
                        .with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void clampsCandidatePageLimitToTheSupportedRange() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-CLAMP", "Gói khác", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "-100").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "500").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void scansPastARejectedCoarseBatchWithoutEndingPagination() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID categoryId = items.findById(fixture.itemId()).orElseThrow().getPrimaryCategoryId();
        UUID earlyItemId = new UUID(0, 500);
        CatalogItemEntity earlyItem = CatalogItemEntity.create(earlyItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Cà phê thêm",
                "early-" + UUID.randomUUID(), null, now);
        earlyItem.publish(now);
        items.saveAndFlush(earlyItem);
        for (int index = 1; index <= 31; index++) {
            UUID variantId = variants.saveAndFlush(VariantEntity.create(new UUID(0, index), earlyItemId,
                    "TRACKED-" + index, "Hết kho " + index, index == 1, index, now)).getId();
            offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                    variantId, 69000, "VND", true, InventoryMode.TRACKED, null, now));
        }
        UUID validVariantId = variants.saveAndFlush(VariantEntity.create(new UUID(0, 100), earlyItemId,
                "VALID", "Còn hàng", false, 100, now)).getId();
        UUID validOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), validVariantId, 69000, "VND", true,
                InventoryMode.UNTRACKED, null, now)).getId();

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(validOfferId.toString()))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void continuesAfterTheRejectedCandidateScanCeiling() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID categoryId = items.findById(fixture.itemId()).orElseThrow().getPrimaryCategoryId();
        UUID earlyItemId = new UUID(0, UUID.randomUUID().getLeastSignificantBits());
        CatalogItemEntity earlyItem = CatalogItemEntity.create(earlyItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Hết kho hàng loạt",
                "scan-ceiling-" + UUID.randomUUID(), null, now);
        earlyItem.publish(now);
        items.saveAndFlush(earlyItem);
        jdbc.update("""
                insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,
                                     sort_order,created_at,updated_at,version)
                select gen_random_uuid(), ?, null, 'Hết kho ' || sequence_number, false, 'ACTIVE',
                       sequence_number, ?, ?, 0
                from generate_series(1,501) sequence_number
                """, earlyItemId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbc.update("""
                insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,
                                              is_available,inventory_mode,created_at,updated_at,version)
                select gen_random_uuid(), ?, v.id, 45000, 'VND', true, 'TRACKED', ?, ?, 0
                from variants v where v.catalog_item_id = ?
                """, fixture.locationId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), earlyItemId);

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void acceptsAnUnchangedReturnedCandidateOnTheResponseCommand() throws Exception {
        Fixture fixture = visibleFixture();
        String readBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String returnedId = new ObjectMapper().readTree(readBody)
                .path("items").get(0).path("locationVariantId").asText();
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(returnedId)));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(returnedId));
    }

    @Test
    void revalidatesAvailabilityAfterACandidateRead() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()));
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), false, offer.getInventoryMode(),
                offer.getPreparationTimeMinutes(), Instant.now());
        offers.saveAndFlush(offer);
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NEED_RESPONSE_ITEM_INVALID"));
    }

    private Fixture visibleFixture() {
        return visibleFixture(NeedType.PRODUCT);
    }

    private Fixture visibleFixture(NeedType type) {
        Actor seller = actor();
        Actor buyer = actor();
        Instant now = Instant.now();
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Seller", "seller-" + merchantId, now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId,
                seller.userId(), MerchantRole.OWNER, now));
        UUID locationId = locations.saveAndFlush(location(merchantId, "Điểm chính", type == NeedType.PRODUCT,
                type == NeedType.SERVICE)).getId();
        if (type == NeedType.SERVICE) {
            jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'SERVICE_AREA',true,5000,false,'ACTIVE',?,?,0)",
                    UUID.randomUUID(), locationId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Cà phê",
                "coffee-" + UUID.randomUUID(), type == NeedType.PRODUCT ? CategoryCatalogType.PRODUCT
                        : CategoryCatalogType.SERVICE, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId,
                type == NeedType.PRODUCT ? CatalogItemType.PRODUCT : CatalogItemType.SERVICE,
                ListingKind.STANDARD, categoryId, "Cà phê", "coffee-" + itemId, null, now);
        item.publish(now);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), itemId,
                "CF-1", "Mặc định", true, 0, now)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId,
                45000, "VND", true, type == NeedType.PRODUCT ? InventoryMode.UNTRACKED : null, null, now)).getId();
        UUID needId = needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(), buyer.userId(),
                type, categoryId, "Cần cà phê", "Cà phê", null, 1L, null,
                point(), new ObjectMapper().createObjectNode(), 5000, now.plus(1, ChronoUnit.DAYS), now)).getId();
        return new Fixture(seller, merchantId, locationId, needId, itemId, variantId, offerId);
    }

    private MerchantLocationEntity location(UUID merchantId, String name, boolean pickup) {
        return location(merchantId, name, pickup, false);
    }

    private MerchantLocationEntity location(UUID merchantId, String name, boolean pickup, boolean service) {
        return MerchantLocationEntity.create(UUID.randomUUID(), merchantId, name, "1 CoVie", null,
                point(), PinSource.GPS, "Asia/Ho_Chi_Minh", pickup, false, service,
                null, "VND", Instant.now());
    }

    private Point point() {
        Point point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        return point;
    }

    private Actor actor() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Seller", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private String path() {
        return "/api/v1/merchants/{merchantId}/need-requests/{needId}/eligible-items";
    }

    private record Actor(UUID userId, UUID sessionId) {
        JwtRequestPostProcessor jwt() {
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString())
                            .claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    private record Fixture(Actor seller, UUID merchantId, UUID locationId, UUID needId,
            UUID itemId, UUID variantId, UUID offerId) {}
}
