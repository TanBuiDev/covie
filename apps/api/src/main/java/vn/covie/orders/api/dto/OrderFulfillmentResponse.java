package vn.covie.orders.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.ProductFulfillmentMethod;

public record OrderFulfillmentResponse(ProductFulfillmentMethod productMethod, UUID pickupLocationId,
        UUID addressId, Integer distanceMeters, JsonNode pickupLocation, JsonNode address, JsonNode buyerContact,
        JsonNode merchantContact, Instant sellerMarkedReadyAt, Instant outForDeliveryAt, Instant sellerMarkedDeliveredAt,
        Instant providerMarkedPerformedAt, Instant awaitingBuyerConfirmationAt, Instant autoCompleteAt,
        Instant noShowEligibleAt) {}
