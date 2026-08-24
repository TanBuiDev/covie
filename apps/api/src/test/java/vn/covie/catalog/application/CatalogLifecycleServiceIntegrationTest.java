package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.catalog.policy.CatalogPublishDecision;
import vn.covie.catalog.policy.CatalogPublishPolicy;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.moderation.ModerationCaseWriter;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CatalogLifecycleServiceIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired CatalogLifecycleService lifecycle;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired OutboxEventRepository outbox;
    @MockitoBean CatalogPublishPolicy policy;
    @MockitoBean ModerationCaseWriter moderationCases;

    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void publishesThenPausesActivatesAndArchivesWithOneOutboxIntentPerStateChange() {
        Fixture fixture = publishableFixture(MerchantRole.OWNER);
        authenticate(fixture.ownerId());
        when(policy.decide(any())).thenReturn(CatalogPublishDecision.PUBLISH_NOW);
        long eventsBefore = outbox.count();

        assertThat(lifecycle.publish(fixture.merchantId(), fixture.itemId(), "\"v0\"").version()).isEqualTo(1);
        assertThat(lifecycle.pause(fixture.merchantId(), fixture.itemId(), "\"v1\"").version()).isEqualTo(2);
        assertThat(lifecycle.activate(fixture.merchantId(), fixture.itemId(), "\"v2\"").version()).isEqualTo(3);
        assertThat(lifecycle.archive(fixture.merchantId(), fixture.itemId(), "\"v3\"").version()).isEqualTo(4);

        CatalogItemEntity item = items.findById(fixture.itemId()).orElseThrow();
        assertThat(item.getListingStatus()).isEqualTo(ListingStatus.DELETED);
        assertThat(item.getDeletedAt()).isNotNull();
        assertThat(outbox.count()).isEqualTo(eventsBefore + 4);
    }

    @Test
    void requiresModerationWithoutPublishingWhenThePolicySaysSo() {
        Fixture fixture = publishableFixture(MerchantRole.OWNER);
        authenticate(fixture.ownerId());
        when(policy.decide(any())).thenReturn(CatalogPublishDecision.REQUIRE_MODERATION);
        long eventsBefore = outbox.count();

        lifecycle.publish(fixture.merchantId(), fixture.itemId(), "\"v0\"");

        CatalogItemEntity item = items.findById(fixture.itemId()).orElseThrow();
        assertThat(item.getListingStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(item.getModerationStatus().name()).isEqualTo("PENDING");
        assertThat(outbox.count()).isEqualTo(eventsBefore);
        verify(moderationCases).openPreApproval(new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM, fixture.itemId()),
                "PUBLISH_REQUIRES_MODERATION");
    }

    @Test
    void rejectsInvalidTransitionsWithoutWritingAnOutboxEvent() {
        Fixture fixture = publishableFixture(MerchantRole.OWNER);
        authenticate(fixture.ownerId());
        when(policy.decide(any())).thenReturn(CatalogPublishDecision.PUBLISH_NOW);
        long eventsBefore = outbox.count();
        assertThatThrownBy(() -> lifecycle.pause(fixture.merchantId(), fixture.itemId(), "\"v0\""))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.CATALOG_LIFECYCLE_INVALID);
        assertThat(items.findById(fixture.itemId()).orElseThrow().getListingStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(outbox.count()).isEqualTo(eventsBefore);
    }

    private Fixture publishableFixture(MerchantRole role) {
        UUID ownerId = UUID.randomUUID(); UUID merchantId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, ownerId, role, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD,
                categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true,
                InventoryMode.UNTRACKED, null, NOW));
        return new Fixture(ownerId, merchantId, itemId);
    }

    private static void authenticate(UUID userId) {
        Jwt jwt = new Jwt("test", NOW, NOW.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "sessionId", UUID.randomUUID().toString(), "roles", List.of("USER")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private record Fixture(UUID ownerId, UUID merchantId, UUID itemId) {}
}
