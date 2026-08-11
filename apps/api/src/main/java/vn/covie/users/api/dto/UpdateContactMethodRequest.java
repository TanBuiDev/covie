package vn.covie.users.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateContactMethodRequest(@NotBlank @Size(max = 500) String value, boolean primary) {}
