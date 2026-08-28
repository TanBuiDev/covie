package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.covie.merchants.model.MerchantKind;

public record CreateMerchantRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 5000) String description,
        MerchantKind merchantKind) {
    public CreateMerchantRequest(String name, String description) {
        this(name, description, MerchantKind.SHOP);
    }
}
