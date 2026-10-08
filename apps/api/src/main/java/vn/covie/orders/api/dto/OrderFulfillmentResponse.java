package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.ProductFulfillmentMethod;

public record OrderFulfillmentResponse(ProductFulfillmentMethod productMethod, UUID pickupLocationId,
        UUID addressId, Integer distanceMeters, Object pickupLocation, Object providerLocation, Object address, Object buyerContact,
        Object merchantContact, Object shippingPolicy, Instant sellerMarkedReadyAt, Instant outForDeliveryAt, Instant sellerMarkedDeliveredAt,
        Instant providerMarkedPerformedAt, Instant awaitingBuyerConfirmationAt, Instant autoCompleteAt,
        Instant noShowEligibleAt) {}
