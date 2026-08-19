package vn.covie.catalog.api.dto.create;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record OptionValueCreateRequest(@NotBlank String clientKey, @NotBlank @Size(max=160) String value) {}
