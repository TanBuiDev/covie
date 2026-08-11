package vn.covie.users.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(@NotBlank @Size(max = 120) String displayName) {}
