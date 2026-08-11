package vn.covie.users.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.covie.users.model.ContactType;

public record CreateContactMethodRequest(@NotNull ContactType type, @NotBlank @Size(max = 500) String value, boolean primary) {}
