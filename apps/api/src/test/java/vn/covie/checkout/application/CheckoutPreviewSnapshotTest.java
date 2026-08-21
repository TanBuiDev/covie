package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shipping.application.ShippingEvaluator;
import vn.covie.shipping.persistence.ShippingPolicyRepository;
import vn.covie.shipping.persistence.ShippingRuleRepository;
import vn.covie.users.persistence.UserAddressRepository;

class CheckoutPreviewSnapshotTest {
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
                mock(ShippingEvaluator.class), mock(CheckoutSessionService.class), new ObjectMapper(), mock(UserAddressRepository.class));

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
        CheckoutPreviewService service = new CheckoutPreviewService(actors, candidates, offers, locations,
                mock(InventoryBalanceRepository.class), mock(ShippingPolicyRepository.class), mock(ShippingRuleRepository.class),
                mock(ShippingEvaluator.class), sessions, new ObjectMapper(), mock(UserAddressRepository.class));

        service.preview(new CheckoutPreviewRequest(CheckoutSourceType.CART, UUID.randomUUID(), null,
                FulfillmentMethod.PICKUP, null, PaymentMethod.COD));

        ArgumentCaptor<JsonNode> snapshot = ArgumentCaptor.forClass(JsonNode.class);
        verify(sessions).create(eq(buyer), eq(CheckoutSourceType.CART), any(), eq(7L), any(), snapshot.capture());
        assertThat(snapshot.getValue().path("items").get(0).path("locationVariantId").asText()).isEqualTo(offerId.toString());
        assertThat(snapshot.getValue().path("items").get(0).path("unitPrice").asLong()).isEqualTo(20_000L);
        assertThat(snapshot.getValue().path("sourceCartVersion").asLong()).isEqualTo(7L);
        assertThat(snapshot.getValue().path("fulfillmentMethod").asText()).isEqualTo("PICKUP");
    }
}
