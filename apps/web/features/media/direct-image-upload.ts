import { completeUpload, createUpload, type MediaPurpose, type MediaResponse } from "./media-api";

export const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;
export const SUPPORTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
export type SupportedImageType = (typeof SUPPORTED_IMAGE_TYPES)[number];

export function validateImageFile(file: File): asserts file is File {
  if (!(SUPPORTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    throw new Error("MEDIA_TYPE_NOT_SUPPORTED");
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    throw new Error("MEDIA_FILE_TOO_LARGE");
  }
}

/**
 * Creates a short-lived upload, sends bytes directly to object storage, then
 * asks the API to verify and complete the media record.
 */
export async function uploadImage(file: File, purpose: MediaPurpose): Promise<MediaResponse> {
  validateImageFile(file);
  const create = await createUpload({ purpose, contentType: file.type, fileSize: file.size });
  if (!create.mediaId || !create.uploadUrl) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");

  const upload = await fetch(create.uploadUrl, {
    method: "PUT",
    headers: create.requiredHeaders ?? {},
    body: file,
  });
  if (!upload.ok) throw new Error("OBJECT_UPLOAD_FAILED");

  return completeUpload(create.mediaId, crypto.randomUUID());
}
