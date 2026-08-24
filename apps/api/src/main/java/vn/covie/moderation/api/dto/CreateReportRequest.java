package vn.covie.moderation.api.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
import vn.covie.moderation.model.ModerationTargetType;
public record CreateReportRequest(@NotNull ModerationTargetType targetType,@NotNull UUID targetId,UUID contextMerchantId,@NotBlank @Size(max=60) String reasonCode,@Size(max=2000) String note) {}
