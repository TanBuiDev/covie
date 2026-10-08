package vn.covie.ads.api;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record AdEventRequest(@NotBlank @Size(max=256) String deliveryToken, @NotNull UUID eventId,
    @NotNull Type type, @Min(0) @Max(3600000) long visibleMillis) {
    public enum Type { IMPRESSION, CLICK }
}
