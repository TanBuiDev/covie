package vn.covie.social.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSocialCommentRequest(@NotBlank @Size(min=2, max=1000) String body) {}
