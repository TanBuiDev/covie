package vn.covie.catalog.api.dto.create;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
public record VariantCreateRequest(@NotBlank String clientKey, @Size(max=120) String sku,
        @NotBlank @Size(max=240) String name, List<String> optionValueKeys) {}
