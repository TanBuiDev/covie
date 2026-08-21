package vn.covie.checkout.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutCommitRequest(@NotBlank String checkoutToken) {}
