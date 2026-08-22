package vn.covie.orders.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueRequest(@NotBlank @Size(max = 1000) String reasonText) {}
