package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.JsonNode;

public record RevalidatedCheckoutItem(CheckoutCandidateItem candidateItem, long unitPrice, JsonNode itemSnapshot) {}
