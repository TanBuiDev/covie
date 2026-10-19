import { completeUpload, createUpload, type MediaResponse, type MediaUploadResponse } from "./media-api";

export function validateCatalogVideoFile(file: File) {
  if (!["video/mp4", "video/webm"].includes(file.type)) throw new Error("MEDIA_TYPE_NOT_SUPPORTED");
  if (file.size <= 0 || file.size > 50 * 1024 * 1024) throw new Error("MEDIA_FILE_TOO_LARGE");
}

export function createCatalogVideoUpload(file: File) {
  validateCatalogVideoFile(file);
  let pending: MediaUploadResponse | undefined;
  let uploaded = false;
  const completionKey = crypto.randomUUID();
  return async (progress?: (message: string) => void): Promise<MediaResponse> => {
    if (!pending) pending = await createUpload({ purpose: "CATALOG_VIDEO", contentType: file.type, fileSize: file.size });
    if (!pending.mediaId || !pending.uploadUrl) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
    if (!uploaded) {
      progress?.("Đang tải video…");
      const response = await fetch(pending.uploadUrl, { method: "PUT", headers: pending.requiredHeaders ?? {}, body: file });
      if (!response.ok && response.status !== 412) throw new Error("OBJECT_UPLOAD_FAILED");
      uploaded = true;
    }
    progress?.("Đang xác minh video…");
    const completed = await completeUpload(pending.mediaId, completionKey);
    if (!completed.verifiedAt || completed.mediaId !== pending.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
    return completed;
  };
}
