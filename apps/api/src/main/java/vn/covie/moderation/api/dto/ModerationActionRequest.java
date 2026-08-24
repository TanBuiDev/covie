package vn.covie.moderation.api.dto;

import jakarta.validation.constraints.Size;

public record ModerationActionRequest(@Size(max = 60) String reasonCode, @Size(max = 2000) String note) {}
