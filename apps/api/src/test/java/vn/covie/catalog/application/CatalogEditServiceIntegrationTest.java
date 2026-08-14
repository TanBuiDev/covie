package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
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
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CatalogEditServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired LocationVariantRepository offers;
    @Autowired MerchantProfileRepository merchants; @Autowired MerchantLocationRepository locations;
    @Autowired CatalogItemRepository items; @Autowired VariantRepository variants;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users; @Autowired MerchantMembershipRepository memberships; @Autowired CatalogEditService edits;

    @Test
    void staleLocationOfferWriteRaisesTheOptimisticConflictHandledAsResourceVersionConflict() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z"); UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "m-" + merchantId, now));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, now)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID(); items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, now));
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", now)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId,
                25000, "VND", true, InventoryMode.UNTRACKED, null, Instant.parse("2026-01-01T00:00:00Z"))).getId();
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin(); second.getTransaction().begin();
            LocationVariantEntity stale = first.find(LocationVariantEntity.class, offerId);
            LocationVariantEntity winner = second.find(LocationVariantEntity.class, offerId);
            winner.update(26000, true, InventoryMode.UNTRACKED, null, Instant.parse("2026-01-01T00:01:00Z"));
            second.getTransaction().commit();
            stale.update(27000, true, InventoryMode.UNTRACKED, null, Instant.parse("2026-01-01T00:02:00Z"));
            assertThatThrownBy(first::flush).isInstanceOf(OptimisticLockException.class);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close();
        }
    }

    @Test
    void concurrentVariantDeletesLeaveOneNonDeletedVariant() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z"); UUID merchantId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", now));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "delete-" + merchantId, now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, ownerId, MerchantRole.OWNER, now));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "delete-food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID(); items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Coffee", "delete-coffee-" + itemId, null, now));
        UUID firstVariantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "First", now)).getId();
        UUID secondVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), itemId, null, "Second", false, 1, now)).getId();
        CountDownLatch start = new CountDownLatch(1); ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> first = executor.submit(deleteAsOwner(start, ownerId, merchantId, itemId, firstVariantId));
            Future<ErrorCode> second = executor.submit(deleteAsOwner(start, ownerId, merchantId, itemId, secondVariantId));
            start.countDown();
            assertThat(java.util.Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder(null, ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID);
            assertThat(variants.countByCatalogItemIdAndDeletedAtIsNull(itemId)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private Callable<ErrorCode> deleteAsOwner(CountDownLatch start, UUID ownerId, UUID merchantId, UUID itemId, UUID variantId) {
        return () -> {
            start.await(); SecurityContextHolder.getContext().setAuthentication(authentication(ownerId));
            try { edits.deleteVariant(merchantId, itemId, variantId, "\"v0\""); return null; }
            catch (ApiException exception) { return exception.code(); }
            finally { SecurityContextHolder.clearContext(); }
        };
    }

    private static JwtAuthenticationToken authentication(UUID ownerId) {
        Instant now = Instant.now(); Jwt jwt = new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", ownerId.toString(), "sessionId", UUID.randomUUID().toString(), "roles", List.of("USER")));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
