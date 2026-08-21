package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.api.dto.CheckoutPreviewResponse;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.shipping.application.ShippingEvaluator;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.persistence.ShippingPolicyRepository;
import vn.covie.shipping.persistence.ShippingRuleRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CheckoutPreviewService {
    private final CurrentActorProvider actors;
    private final CheckoutCandidateResolverRegistry candidates;
    private final LocationVariantRepository offers;
    private final MerchantLocationRepository locations;
    private final InventoryBalanceRepository balances;
    private final ShippingPolicyRepository policies;
    private final ShippingRuleRepository rules;
    private final ShippingEvaluator shipping;
    private final CheckoutSessionService sessions;
    private final ObjectMapper mapper;

    public CheckoutPreviewService(CurrentActorProvider actors, CheckoutCandidateResolverRegistry candidates,
            LocationVariantRepository offers, MerchantLocationRepository locations,
            InventoryBalanceRepository balances, ShippingPolicyRepository policies,
            ShippingRuleRepository rules, ShippingEvaluator shipping, CheckoutSessionService sessions,
            ObjectMapper mapper) {
        this.actors = actors; this.candidates = candidates; this.offers = offers; this.locations = locations;
        this.balances = balances; this.policies = policies; this.rules = rules; this.shipping = shipping;
        this.sessions = sessions; this.mapper = mapper;
    }

    @Transactional
    public CheckoutPreviewResponse preview(CheckoutPreviewRequest request) {
        UUID user = actors.currentActor().userId();
        CheckoutCandidate candidate = candidates.resolve(user, request);
        var location = locations.findById(candidate.merchantLocationId()).orElseThrow(
                () -> error(ErrorCode.CHECKOUT_LOCATION_UNAVAILABLE, "Location unavailable."));
        long subtotal = 0;
        java.util.List<CheckoutComparableItem> comparableItems = new java.util.ArrayList<>();
        for (var item : candidate.items()) {
            var offer = offers.findUsableById(item.locationVariantId()).orElseThrow(
                    () -> error(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE, "Item unavailable."));
            if (!offer.isAvailable()) throw error(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE, "Item unavailable.");
            if (candidate.itemType() == CatalogItemType.SERVICE && item.quantity() != 1) {
                throw error(ErrorCode.CHECKOUT_ITEMS_INVALID, "Service quantity must be one.");
            }
            if (offer.getInventoryMode() == InventoryMode.TRACKED) {
                var balance = balances.findById(offer.getId()).orElseThrow();
                if (balance.availableQuantity() < item.quantity()) throw error(ErrorCode.INSUFFICIENT_INVENTORY, "Insufficient inventory.");
            }
            subtotal = Math.addExact(subtotal, Math.multiplyExact(offer.getPrice(), item.quantity()));
            comparableItems.add(new CheckoutComparableItem(offer.getId(), item.quantity(), offer.getPrice(), true));
        }
        Long fee = 0L; Long total = subtotal; long known = subtotal;
        if (request.fulfillmentMethod() == FulfillmentMethod.SERVICE) {
            if (!location.isServiceEnabled() || request.addressId() == null) throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Service requires an address and enabled service location.");
            fee = null;
        } else if (request.fulfillmentMethod() == FulfillmentMethod.SELLER_DELIVERY) {
            if (!location.isSellerDeliveryEnabled() || request.addressId() == null) throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Seller delivery requires an address and enabled delivery location.");
            var policy = policies.findFirstByMerchantLocationIdAndPolicyStatus(location.getId(), ShippingPolicyStatus.ACTIVE).orElseThrow(() -> error(ErrorCode.CHECKOUT_LOCATION_UNAVAILABLE, "Shipping policy unavailable."));
            var evaluation = shipping.evaluate(policy, rules.findByShippingPolicyIdOrderByPriority(policy.getId()), subtotal, 0);
            fee = evaluation.shippingFeeMinor(); total = evaluation.totalMinor(); known = evaluation.knownTotalMinor();
        } else if (request.fulfillmentMethod() != FulfillmentMethod.PICKUP) {
            throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Fulfillment method is required.");
        }
        CheckoutComparableSnapshot comparableSnapshot = new CheckoutComparableSnapshot(candidate.sourceType(), location.getId(),
                candidate.sourceCartVersion(), request.fulfillmentMethod(), request.addressId(), subtotal,
                location.getCurrencyCode(), comparableItems);
        var stored = sessions.create(user, candidate.sourceType(), mapper.valueToTree(candidate), candidate.sourceCartVersion(),
                mapper.valueToTree(request), mapper.valueToTree(comparableSnapshot));
        return new CheckoutPreviewResponse(stored.checkoutToken(), stored.expiresAt(), location.getId(), subtotal, fee, total, known, location.getCurrencyCode(), request.fulfillmentMethod());
    }

    private static ApiException error(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
