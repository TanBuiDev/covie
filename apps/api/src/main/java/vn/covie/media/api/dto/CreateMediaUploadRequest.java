package vn.covie.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vn.covie.media.model.MediaPurpose;

public record CreateMediaUploadRequest(@NotNull MediaPurpose purpose, @NotBlank String contentType, @Positive long fileSize) {}
