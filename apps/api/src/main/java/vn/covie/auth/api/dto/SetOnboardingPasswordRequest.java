package vn.covie.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetOnboardingPasswordRequest(@NotBlank @Size(min = 10, max = 200) String password) {}
