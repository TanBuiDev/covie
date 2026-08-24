package vn.covie.moderation.api.dto;
import jakarta.validation.constraints.*;import java.util.*;import vn.covie.moderation.model.ModerationActionType;
public record BulkModerationRequest(@NotNull ModerationActionType action,@NotEmpty List<UUID> caseIds,@NotBlank @Size(max=60) String reasonCode,@Size(max=2000) String note) {}
