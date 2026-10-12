package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.delivery.application.CoverageEvaluation;
import vn.covie.delivery.application.CoverageMatchSource;
import vn.covie.delivery.model.CoverageType;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shipping.application.ShippingEvaluator;
import vn.covie.shipping.application.ShippingEvaluation;
import vn.covie.shipping.application.ShippingPolicySnapshotFactory;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyEntity;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.persistence.ShippingPolicyRepository;
import vn.covie.shipping.persistence.ShippingRuleRepository;
import vn.covie.users.persistence.UserAddressRepository;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;

class CheckoutPreviewSnapshotTest {
    @Test
    void usesEligibleProductDeliveryDistanceForShipping() {
        CoverageFixture fixture = coverageFixture(CatalogItemType.PRODUCT, FulfillmentMethod.SELLER_DELIVERY);
        when(fixture.coverage().evaluate(eq(fixture.locationId()), eq(CoverageType.PRODUCT_DELIVERY), any()))
                .thenReturn(new CoverageEvaluation(true, CoverageType.PRODUCT_DELIVERY, 1_450L,
                        java.util.Set.of(CoverageMatchSource.RADIUS), UUID.randomUUID()));
        ShippingPolicyEntity policy = ShippingPolicyEntity.create(UUID.randomUUID(), fixture.locationId(),
                ShippingMode.FIXED, 5_000L, "VND", null, ShippingPolicyStatus.ACTIVE, Instant.now());
        when(fixture.policies().findFirstByMerchantLocationIdAndPolicyStatus(fixture.locationId(), ShippingPolicyStatus.ACTIVE))
                .thenReturn(Optional.of(policy));
        when(fixture.rules().findByShippingPolicyIdOrderByPriority(policy.getId())).thenReturn(List.of());
        when(fixture.shipping().evaluate(policy, List.of(), 20_000L, 1_450L)).thenReturn(
                new ShippingEvaluation(ShippingMode.FIXED, 20_000L, 5_000L, 25_000L, 25_000L,
                        "VND", 1_450L, List.of(), null));

        CheckoutRevalidation result = fixture.service().revalidate(fixture.buyer(), fixture.request());

        verify(fixture.shipping()).evaluate(policy, List.of(), 20_000L, 1_450L);
        assertThat(result.checkout().fulfillmentSnapshots().distanceMeters()).isEqualTo(1_450);
    }

    @Test
    void rejectsSellerDeliveryOutsideProductCoverage() {
        CoverageFixture fixture = coverageFixture(CatalogItemType.PRODUCT, FulfillmentMethod.SELLER_DELIVERY);
        when(fixture.coverage().evaluate(eq(fixture.locationId()), eq(CoverageType.PRODUCT_DELIVERY), any()))
                .thenReturn(CoverageEvaluation.notEligible(CoverageType.PRODUCT_DELIVERY, 9_000L));

        assertThatThrownBy(() -> fixture.service().revalidate(fixture.buyer(), fixture.request()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED);
        verify(fixture.shipping(), never()).evaluate(any(), any(), anyLong(), anyLong());
    }

    @Test
    void acceptsServiceOnlyInsideServiceAreaCoverage() {
        CoverageFixture fixture = coverageFixture(CatalogItemType.SERVICE, FulfillmentMethod.SERVICE);
        when(fixture.coverage().evaluate(eq(fixture.locationId()), eq(CoverageType.SERVICE_AREA), any()))
                .thenReturn(new CoverageEvaluation(true, CoverageType.SERVICE_AREA, 2_100L,
                        java.util.Set.of(CoverageMatchSource.AREA), UUID.randomUUID()));

        CheckoutRevalidation result = fixture.service().revalidate(fixture.buyer(), fixture.request());

        assertThat(result.checkout().fulfillmentSnapshots().distanceMeters()).isEqualTo(2_100);
        verify(fixture.shipping(), never()).evaluate(any(), any(), anyLong(), anyLong());
    }

    @Test
    void rejectsServiceOutsideServiceAreaCoverage() {
        CoverageFixture fixture = coverageFixture(CatalogItemType.SERVICE, FulfillmentMethod.SERVICE);
        when(fixture.coverage().evaluate(eq(fixture.locationId()), eq(CoverageType.SERVICE_AREA), any()))
                .thenReturn(CoverageEvaluation.notEligible(CoverageType.SERVICE_AREA, 12_000L));

        assertThatThrownBy(() -> fixture.service().revalidate(fixture.buyer(), fixture.request()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED);
    }

    @Test
    void pickupDoesNotRequireCoverageOrDistance() {
        CoverageFixture fixture = coverageFixture(CatalogItemType.PRODUCT, FulfillmentMethod.PICKUP);

        CheckoutRevalidation result = fixture.service().revalidate(fixture.buyer(), fixture.request());

        verify(fixture.coverage(), never()).evaluate(any(), any(), any());
        assertThat(result.checkout().fulfillmentSnapshots().distanceMeters()).isNull();
    }

    @Test
    void rejectsPickupWhenTheMerchantLocationDoesNotEnablePickup() {
        UUID buyer = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(buyer, java.util.Set.of(), UUID.randomUUID()));
        CheckoutCandidateResolverRegistry candidates = mock(CheckoutCandidateResolverRegistry.class);
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, buyer, UUID.randomUUID(), locationId,
                CatalogItemType.PRODUCT, List.of(new CheckoutCandidateItem(offerId, UUID.randomUUID(), UUID.randomUUID(), 1)),
                null, null);
        when(candidates.resolve(eq(buyer), any())).thenReturn(candidate);
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findUsableById(offerId)).thenReturn(Optional.of(LocationVariantEntity.create(offerId, locationId,
                candidate.items().getFirst().variantId(), 20_000, "VND", true, InventoryMode.UNTRACKED, null, Instant.now())));
        MerchantLocationRepository locations = mock(MerchantLocationRepository.class);
        MerchantLocationEntity location = mock(MerchantLocationEntity.class);
        when(location.getId()).thenReturn(locationId); when(location.getCurrencyCode()).thenReturn("VND");
        when(location.isPickupEnabled()).thenReturn(false);
        when(locations.findById(locationId)).thenReturn(Optional.of(location));
        CheckoutPreviewService service = new CheckoutPreviewService(actors, candidates, offers, locations,
                mock(InventoryBalanceRepository.class), mock(ShippingPolicyRepository.class), mock(ShippingRuleRepository.class),
                mock(ShippingEvaluator.class), mock(CheckoutSessionService.class), new ObjectMapper(), mock(UserAddressRepository.class),
                mock(DeliveryCoverageEvaluator.class), mock(CatalogItemRepository.class), mock(VariantRepository.class),
                mock(MerchantContactMethodRepository.class), mock(CheckoutSnapshotFactory.class),
                mock(ShippingPolicySnapshotFactory.class), mock(CheckoutTradingPolicy.class));

        assertThatThrownBy(() -> service.preview(new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(offerId, 1)), FulfillmentMethod.PICKUP, null, PaymentMethod.COD)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED);
    }

    @Test
    void storesSemanticSnapshotForLaterCommitComparison() {
        UUID buyer = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(buyer, java.util.Set.of(), UUID.randomUUID()));
        CheckoutCandidateResolverRegistry candidates = mock(CheckoutCandidateResolverRegistry.class);
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.CART, buyer, UUID.randomUUID(), locationId,
                CatalogItemType.PRODUCT, List.of(new CheckoutCandidateItem(offerId, UUID.randomUUID(), UUID.randomUUID(), 2)),
                7L, new ObjectMapper().createObjectNode());
        when(candidates.resolve(eq(buyer), any())).thenReturn(candidate);
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findUsableById(offerId)).thenReturn(Optional.of(LocationVariantEntity.create(offerId, locationId,
                candidate.items().getFirst().variantId(), 20_000, "VND", true, InventoryMode.UNTRACKED, null, Instant.now())));
        MerchantLocationRepository locations = mock(MerchantLocationRepository.class);
        MerchantLocationEntity location = mock(MerchantLocationEntity.class);
        when(location.getId()).thenReturn(locationId); when(location.getCurrencyCode()).thenReturn("VND");
        when(location.isPickupEnabled()).thenReturn(true);
        when(locations.findById(locationId)).thenReturn(Optional.of(location));
        CheckoutSessionService sessions = mock(CheckoutSessionService.class);
        when(sessions.create(any(), any(), any(), any(), any(), any())).thenReturn(
                new CreatedCheckoutSession(UUID.randomUUID(), "chk_test", Instant.parse("2026-08-22T00:15:00Z")));
        CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
        VariantRepository variants = mock(VariantRepository.class);
        when(catalogItems.findById(candidate.items().getFirst().catalogItemId())).thenReturn(Optional.of(
                CatalogItemEntity.create(candidate.items().getFirst().catalogItemId(), candidate.merchantProfileId(),
                        CatalogItemType.PRODUCT, ListingKind.STANDARD, UUID.randomUUID(), "Sản phẩm", "san-pham", null, Instant.now())));
        when(variants.findByIdAndCatalogItemIdAndDeletedAtIsNull(candidate.items().getFirst().variantId(),
                candidate.items().getFirst().catalogItemId())).thenReturn(Optional.of(VariantEntity.createDefault(
                        candidate.items().getFirst().variantId(), candidate.items().getFirst().catalogItemId(), "Mặc định", Instant.now())));
        CheckoutSnapshotFactory snapshotFactory = mock(CheckoutSnapshotFactory.class);
        when(snapshotFactory.item(any(), any(), any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.location(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.buyerContact(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.merchantContact(any())).thenReturn(new ObjectMapper().createObjectNode());
        CheckoutPreviewService service = new CheckoutPreviewService(actors, candidates, offers, locations,
                mock(InventoryBalanceRepository.class), mock(ShippingPolicyRepository.class), mock(ShippingRuleRepository.class),
                mock(ShippingEvaluator.class), sessions, new ObjectMapper(), mock(UserAddressRepository.class),
                mock(DeliveryCoverageEvaluator.class), catalogItems, variants, mock(MerchantContactMethodRepository.class),
                snapshotFactory, mock(ShippingPolicySnapshotFactory.class), mock(CheckoutTradingPolicy.class));

        service.preview(new CheckoutPreviewRequest(CheckoutSourceType.CART, UUID.randomUUID(), null,
                FulfillmentMethod.PICKUP, null, PaymentMethod.COD));

        ArgumentCaptor<JsonNode> snapshot = ArgumentCaptor.forClass(JsonNode.class);
        verify(sessions).create(eq(buyer), eq(CheckoutSourceType.CART), any(), eq(7L), any(), snapshot.capture());
        assertThat(snapshot.getValue().path("items").get(0).path("locationVariantId").asText()).isEqualTo(offerId.toString());
        assertThat(snapshot.getValue().path("items").get(0).path("unitPrice").asLong()).isEqualTo(20_000L);
        assertThat(snapshot.getValue().path("sourceCartVersion").asLong()).isEqualTo(7L);
        assertThat(snapshot.getValue().path("fulfillmentMethod").asText()).isEqualTo("PICKUP");
    }

    private static CoverageFixture coverageFixture(CatalogItemType type, FulfillmentMethod fulfillment) {
        UUID buyer = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(buyer, java.util.Set.of(), UUID.randomUUID()));
        CheckoutCandidateResolverRegistry candidates = mock(CheckoutCandidateResolverRegistry.class);
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, buyer, UUID.randomUUID(), locationId,
                type, List.of(new CheckoutCandidateItem(offerId, variantId, itemId, 1)), null, null);
        when(candidates.resolve(eq(buyer), any())).thenReturn(candidate);
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findUsableById(offerId)).thenReturn(Optional.of(LocationVariantEntity.create(offerId, locationId,
                variantId, 20_000, "VND", true, InventoryMode.UNTRACKED, 15, Instant.now())));
        MerchantLocationRepository locations = mock(MerchantLocationRepository.class);
        MerchantLocationEntity location = mock(MerchantLocationEntity.class);
        when(location.getId()).thenReturn(locationId);
        when(location.getName()).thenReturn("Điểm bán");
        when(location.getAddressText()).thenReturn("1 Đường Mới");
        when(location.getCurrencyCode()).thenReturn("VND");
        when(location.isPickupEnabled()).thenReturn(fulfillment == FulfillmentMethod.PICKUP);
        when(location.isSellerDeliveryEnabled()).thenReturn(fulfillment == FulfillmentMethod.SELLER_DELIVERY);
        when(location.isServiceEnabled()).thenReturn(fulfillment == FulfillmentMethod.SERVICE);
        when(locations.findById(locationId)).thenReturn(Optional.of(location));
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        if (fulfillment != FulfillmentMethod.PICKUP) {
            UserAddressEntity address = UserAddressEntity.create(addressId, buyer, "Nhà", "Người mua", "0900000000",
                    "2 Đường Mới", 10.8, 106.7, PinSource.USER_ADJUSTED, null, true, Instant.now());
            when(addresses.findByIdAndUserIdAndStatus(addressId, buyer, "ACTIVE")).thenReturn(Optional.of(address));
        }
        ShippingPolicyRepository policies = mock(ShippingPolicyRepository.class);
        ShippingRuleRepository rules = mock(ShippingRuleRepository.class);
        ShippingEvaluator shipping = mock(ShippingEvaluator.class);
        DeliveryCoverageEvaluator coverage = mock(DeliveryCoverageEvaluator.class);
        CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
        VariantRepository variants = mock(VariantRepository.class);
        when(catalogItems.findById(itemId)).thenReturn(Optional.of(CatalogItemEntity.create(itemId,
                candidate.merchantProfileId(), type, ListingKind.STANDARD, UUID.randomUUID(), "Mục", "muc", null, Instant.now())));
        when(variants.findByIdAndCatalogItemIdAndDeletedAtIsNull(variantId, itemId)).thenReturn(Optional.of(
                VariantEntity.createDefault(variantId, itemId, "Mặc định", Instant.now())));
        CheckoutSnapshotFactory snapshotFactory = mock(CheckoutSnapshotFactory.class);
        when(snapshotFactory.item(any(), any(), any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.location(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.address(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.buyerContact(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(snapshotFactory.merchantContact(any())).thenReturn(new ObjectMapper().createObjectNode());
        CheckoutPreviewService service = new CheckoutPreviewService(actors, candidates, offers, locations,
                mock(InventoryBalanceRepository.class), policies, rules, shipping,
                mock(CheckoutSessionService.class), new ObjectMapper(), addresses, coverage, catalogItems, variants,
                mock(MerchantContactMethodRepository.class), snapshotFactory, mock(ShippingPolicySnapshotFactory.class), mock(CheckoutTradingPolicy.class));
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(offerId, 1)), fulfillment,
                fulfillment == FulfillmentMethod.PICKUP ? null : addressId, PaymentMethod.COD);
        return new CoverageFixture(service, buyer, locationId, request, coverage, policies, rules, shipping);
    }

    private record CoverageFixture(CheckoutPreviewService service, UUID buyer, UUID locationId,
            CheckoutPreviewRequest request, DeliveryCoverageEvaluator coverage, ShippingPolicyRepository policies,
            ShippingRuleRepository rules, ShippingEvaluator shipping) {}
}
