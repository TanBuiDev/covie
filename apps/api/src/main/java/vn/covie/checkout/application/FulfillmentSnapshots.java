package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record FulfillmentSnapshots(
        UUID buyerAddressId,
        Integer distanceMeters,
        JsonNode merchantLocation,
        JsonNode buyerAddress,
        JsonNode buyerContact,
        JsonNode merchantContact,
        JsonNode shippingPolicy) {}
