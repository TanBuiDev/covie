package vn.covie.admin.api.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record AdminReasonRequest(@NotBlank @Size(max = 60) String reasonCode, @Size(max = 2000) String note) {}
