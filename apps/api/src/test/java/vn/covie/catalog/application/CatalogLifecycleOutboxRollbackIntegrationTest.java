package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.outbox.OutboxEventEntity;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CatalogLifecycleOutboxRollbackIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired CatalogLifecycleService lifecycle;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository users; @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships; @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories; @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants; @Autowired LocationVariantRepository offers;
    @Autowired OutboxEventRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OutboxEventWriter writer;

    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void archiveRollsBackTheFlushedListingAndWriterOutboxEventWhenTheWriterFails() {
        Fixture fixture = fixture(); authenticate(fixture.ownerId());
        long eventsBefore = outbox.count();
        doAnswer(invocation -> {
            Map<String, Object> persistedItem = jdbc.queryForMap(
                    "select listing_status, version, deleted_at from catalog_items where id = ?", fixture.itemId());
            assertThat(persistedItem.get("listing_status")).isEqualTo("DELETED");
            assertThat(((Number) persistedItem.get("version")).longValue()).isEqualTo(1L);
            assertThat(persistedItem.get("deleted_at")).isNotNull();

            outbox.saveAndFlush(OutboxEventEntity.pending(
                    UUID.randomUUID(), "WRITER_STATE_FOR_ROLLBACK", "CATALOG_ITEM", fixture.itemId(),
                    JsonNodeFactory.instance.objectNode(), NOW));
            assertThat(outbox.count()).isEqualTo(eventsBefore + 1);
            throw new IllegalStateException("outbox write failed after archive flush");
        }).when(writer).append(any());

        assertThatThrownBy(() -> lifecycle.archive(fixture.merchantId(), fixture.itemId(), "\"v0\""))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox write failed after archive flush");

        verify(writer).append(any());
        entityManager.clear();
        CatalogItemEntity reloaded = entityManager.find(CatalogItemEntity.class, fixture.itemId());
        assertThat(reloaded.getListingStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(outbox.count()).isEqualTo(eventsBefore);
    }

    private Fixture fixture() {
        UUID ownerId = UUID.randomUUID(); UUID merchantId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, ownerId, MerchantRole.OWNER, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW));
        return new Fixture(ownerId, merchantId, itemId);
    }

    private static void authenticate(UUID userId) {
        Jwt jwt = new Jwt("test", NOW, NOW.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", userId.toString(), "sessionId", UUID.randomUUID().toString(), "roles", List.of("USER")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
    private record Fixture(UUID ownerId, UUID merchantId, UUID itemId) {}
}
