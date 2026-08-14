package vn.covie.catalog.api.dto.create;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
public record OptionGroupCreateRequest(@NotBlank String clientKey, @NotBlank @Size(max=120) String name,
        List<@Valid OptionValueCreateRequest> values) {}
