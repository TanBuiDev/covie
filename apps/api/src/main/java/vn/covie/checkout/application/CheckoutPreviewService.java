package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.shipping.application.ShippingEvaluation;
import vn.covie.shipping.application.ShippingEvaluator;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.persistence.ShippingPolicyRepository;
import vn.covie.shipping.persistence.ShippingRuleRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.persistence.UserAddressRepository;

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
    private final UserAddressRepository addresses;

    public CheckoutPreviewService(CurrentActorProvider actors, CheckoutCandidateResolverRegistry candidates,
            LocationVariantRepository offers, MerchantLocationRepository locations,
            InventoryBalanceRepository balances, ShippingPolicyRepository policies,
            ShippingRuleRepository rules, ShippingEvaluator shipping, CheckoutSessionService sessions,
            ObjectMapper mapper, UserAddressRepository addresses) {
        this.actors = actors;
        this.candidates = candidates;
        this.offers = offers;
        this.locations = locations;
        this.balances = balances;
        this.policies = policies;
        this.rules = rules;
        this.shipping = shipping;
        this.sessions = sessions;
        this.mapper = mapper;
        this.addresses = addresses;
    }

    @Transactional
    public CheckoutPreviewResponse preview(CheckoutPreviewRequest request) {
        UUID user = actors.currentActor().userId();
        CheckoutRevalidation revalidated = revalidate(user, request);
        var stored = sessions.create(user, revalidated.checkout().candidate().sourceType(),
                mapper.valueToTree(revalidated.checkout().candidate()), revalidated.checkout().candidate().sourceCartVersion(),
                mapper.valueToTree(request), mapper.valueToTree(revalidated.comparableSnapshot()));
        RevalidatedCheckout checkout = revalidated.checkout();
        return new CheckoutPreviewResponse(stored.checkoutToken(), stored.expiresAt(),
                checkout.candidate().merchantLocationId(), checkout.subtotal(), revalidated.shippingFee(),
                revalidated.total(), revalidated.knownTotal(), checkout.currency(), checkout.fulfillmentMethod());
    }

    @Transactional(readOnly = true)
    public CheckoutRevalidation revalidate(UUID user, CheckoutPreviewRequest request) {
        return revalidate(user, request, Map.of());
    }

    public CheckoutRevalidation revalidate(UUID user, CheckoutPreviewRequest request,
            Map<UUID, vn.covie.inventory.model.InventoryBalanceEntity> lockedBalances) {
        CheckoutCandidate candidate = candidates.resolve(user, request);
        MerchantLocationEntity location = locations.findById(candidate.merchantLocationId()).orElseThrow(
                () -> error(ErrorCode.CHECKOUT_LOCATION_UNAVAILABLE, "Location unavailable."));
        validateFulfillmentCapability(request.fulfillmentMethod(), request.addressId(), location);
        UserAddressEntity buyerAddress = request.addressId() == null ? null : addresses
                .findByIdAndUserIdAndStatus(request.addressId(), user, "ACTIVE")
                .orElseThrow(() -> error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Delivery address is unavailable."));

        long subtotal = 0;
        List<CheckoutComparableItem> comparableItems = new ArrayList<>();
        List<RevalidatedCheckoutItem> revalidatedItems = new ArrayList<>();
        java.util.Set<UUID> trackedOfferIds = new java.util.TreeSet<>();
        for (CheckoutCandidateItem item : candidate.items()) {
            var offer = offers.findUsableById(item.locationVariantId()).orElseThrow(
                    () -> error(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE, "Item unavailable."));
            if (!offer.isAvailable()) throw error(ErrorCode.CHECKOUT_ITEM_UNAVAILABLE, "Item unavailable.");
            if (candidate.itemType() == CatalogItemType.SERVICE && item.quantity() != 1) {
                throw error(ErrorCode.CHECKOUT_ITEMS_INVALID, "Service quantity must be one.");
            }
            if (offer.getInventoryMode() == InventoryMode.TRACKED) {
                trackedOfferIds.add(offer.getId());
                var balance = lockedBalances.get(offer.getId());
                if (balance == null) balance = balances.findById(offer.getId()).orElseThrow(
                        () -> error(ErrorCode.INSUFFICIENT_INVENTORY, "Insufficient inventory."));
                if (balance.availableQuantity() < item.quantity()) {
                    throw error(ErrorCode.INSUFFICIENT_INVENTORY, "Insufficient inventory.");
                }
            }
            subtotal = Math.addExact(subtotal, Math.multiplyExact(offer.getPrice(), item.quantity()));
            comparableItems.add(new CheckoutComparableItem(offer.getId(), item.quantity(), offer.getPrice(), true));
            ObjectNode itemSnapshot = mapper.createObjectNode()
                    .put("locationVariantId", offer.getId().toString())
                    .put("variantId", item.variantId().toString())
                    .put("catalogItemId", item.catalogItemId().toString())
                    .put("unitPrice", offer.getPrice())
                    .put("currency", offer.getCurrencyCode());
            revalidatedItems.add(new RevalidatedCheckoutItem(item, offer.getPrice(), itemSnapshot));
        }

        ShippingEvaluation evaluation = null;
        Long fee = 0L;
        Long total = subtotal;
        long knownTotal = subtotal;
        if (request.fulfillmentMethod() == FulfillmentMethod.SERVICE) {
            fee = null;
        } else if (request.fulfillmentMethod() == FulfillmentMethod.SELLER_DELIVERY) {
            var policy = policies.findFirstByMerchantLocationIdAndPolicyStatus(location.getId(), ShippingPolicyStatus.ACTIVE)
                    .orElseThrow(() -> error(ErrorCode.CHECKOUT_LOCATION_UNAVAILABLE, "Shipping policy unavailable."));
            evaluation = shipping.evaluate(policy, rules.findByShippingPolicyIdOrderByPriority(policy.getId()), subtotal, 0);
            fee = evaluation.shippingFeeMinor();
            total = evaluation.totalMinor();
            knownTotal = evaluation.knownTotalMinor();
        }

        ObjectNode locationSnapshot = mapper.createObjectNode().put("id", location.getId().toString())
                .put("name", location.getName()).put("addressText", location.getAddressText());
        ObjectNode buyerAddressSnapshot = buyerAddress == null ? null : mapper.createObjectNode()
                .put("id", buyerAddress.getId().toString()).put("label", buyerAddress.getLabel())
                .put("recipientName", buyerAddress.getRecipientName()).put("phone", buyerAddress.getPhone())
                .put("addressText", buyerAddress.getAddressText()).put("latitude", buyerAddress.getGeoPoint().getY())
                .put("longitude", buyerAddress.getGeoPoint().getX()).put("version", buyerAddress.getVersion());
        FulfillmentSnapshots fulfillment = new FulfillmentSnapshots(request.addressId(), null, locationSnapshot,
                buyerAddressSnapshot, mapper.createObjectNode(), mapper.createObjectNode(),
                evaluation == null ? null : evaluation.policySnapshot());
        RevalidatedCheckout checkout = new RevalidatedCheckout(candidate, request.fulfillmentMethod(), request.paymentMethod(),
                List.copyOf(revalidatedItems), subtotal, location.getCurrencyCode(), evaluation, fulfillment);
        CheckoutComparableSnapshot comparable = new CheckoutComparableSnapshot(candidate.sourceType(), location.getId(),
                candidate.sourceCartVersion(), request.fulfillmentMethod(), request.addressId(),
                buyerAddress == null ? null : buyerAddress.getVersion(), subtotal,
                location.getCurrencyCode(), comparableItems);
        return new CheckoutRevalidation(checkout, comparable, fee, total, knownTotal, evaluation, Set.copyOf(trackedOfferIds));
    }

    private static void validateFulfillmentCapability(FulfillmentMethod method, UUID addressId,
            MerchantLocationEntity location) {
        if (method == FulfillmentMethod.PICKUP) {
            if (!location.isPickupEnabled()) {
                throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Pickup is not enabled at this location.");
            }
        } else if (method == FulfillmentMethod.SELLER_DELIVERY) {
            if (!location.isSellerDeliveryEnabled() || addressId == null) {
                throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED,
                        "Seller delivery requires an address and enabled delivery location.");
            }
        } else if (method == FulfillmentMethod.SERVICE) {
            if (!location.isServiceEnabled() || addressId == null) {
                throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED,
                        "Service requires an address and enabled service location.");
            }
        } else {
            throw error(ErrorCode.CHECKOUT_FULFILLMENT_NOT_SUPPORTED, "Fulfillment method is required.");
        }
    }

    private static ApiException error(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
