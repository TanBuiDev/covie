import { afterEach, expect, it, vi } from "vitest";
import { uploadAdMedia } from "../../features/media/direct-ad-upload";
import { createUpload, completeUpload } from "../../features/media/media-api";

vi.mock("../../features/media/media-api", () => ({ createUpload: vi.fn(), completeUpload: vi.fn() }));
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); });

it("uploads ad video with AD_BANNER purpose and completes only after storage succeeds", async () => {
  vi.mocked(createUpload).mockResolvedValue({ mediaId: "media", uploadUrl: "https://storage.test/video", requiredHeaders: { "Content-Type": "video/webm" } });
  const put = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
  vi.stubGlobal("fetch", put);
  const file = new File(["video"], "ad.webm", { type: "video/webm" });
  await uploadAdMedia(file);
  expect(createUpload).toHaveBeenCalledWith({ purpose: "AD_BANNER", contentType: "video/webm", fileSize: 5 });
  expect(put).toHaveBeenCalledWith("https://storage.test/video", expect.objectContaining({ method: "PUT", body: file }));
  expect(completeUpload).toHaveBeenCalledWith("media", expect.any(String));
});

it("rejects unsupported and oversized media before calling API", async () => {
  await expect(uploadAdMedia(new File(["x"], "x.svg", { type: "image/svg+xml" }))).rejects.toThrow("MEDIA_TYPE_NOT_SUPPORTED");
  const file = new File(["x"], "x.mp4", { type: "video/mp4" });
  Object.defineProperty(file, "size", { value: 50 * 1024 * 1024 + 1 });
  await expect(uploadAdMedia(file)).rejects.toThrow("MEDIA_FILE_TOO_LARGE");
  expect(createUpload).not.toHaveBeenCalled();
});

it("does not complete a failed object upload", async () => {
  vi.mocked(createUpload).mockResolvedValue({ mediaId: "media", uploadUrl: "https://storage.test/video" });
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 403 })));
  await expect(uploadAdMedia(new File(["x"], "x.png", { type: "image/png" }))).rejects.toThrow("OBJECT_UPLOAD_FAILED");
  expect(completeUpload).not.toHaveBeenCalled();
});
