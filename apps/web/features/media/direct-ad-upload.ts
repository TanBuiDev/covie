import { createUpload, completeUpload, type MediaResponse } from "./media-api";
import { MAX_IMAGE_SIZE_BYTES, SUPPORTED_IMAGE_TYPES } from "./direct-image-upload";

export const MAX_AD_VIDEO_SIZE_BYTES = 50 * 1024 * 1024;
export const SUPPORTED_AD_MEDIA_TYPES = [...SUPPORTED_IMAGE_TYPES, "video/mp4", "video/webm"] as const;

export async function uploadAdMedia(file: File): Promise<MediaResponse> {
  if (!(SUPPORTED_AD_MEDIA_TYPES as readonly string[]).includes(file.type)) throw new Error("MEDIA_TYPE_NOT_SUPPORTED");
  const maxBytes = file.type.startsWith("video/") ? MAX_AD_VIDEO_SIZE_BYTES : MAX_IMAGE_SIZE_BYTES;
  if (file.size <= 0 || file.size > maxBytes) throw new Error("MEDIA_FILE_TOO_LARGE");
  const upload = await createUpload({ purpose: "AD_BANNER", contentType: file.type, fileSize: file.size });
  if (!upload.mediaId || !upload.uploadUrl) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
  const response = await fetch(upload.uploadUrl, { method: "PUT", headers: upload.requiredHeaders ?? {}, body: file });
  if (!response.ok) throw new Error("OBJECT_UPLOAD_FAILED");
  return completeUpload(upload.mediaId, crypto.randomUUID());
}
