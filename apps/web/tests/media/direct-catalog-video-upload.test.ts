import { afterEach, expect, it, vi } from "vitest";
import { createCatalogVideoUpload, validateCatalogVideoFile } from "@/features/media/direct-catalog-video-upload";
import * as api from "@/features/media/media-api";

vi.mock("@/features/media/media-api", () => ({ createUpload: vi.fn(), completeUpload: vi.fn() }));
afterEach(() => { vi.resetAllMocks(); vi.unstubAllGlobals(); });
const file = () => new File(["mp4 fixture"], "clip.mp4", { type: "video/mp4" });

it("retries verification using the same media and idempotency key without another upload", async () => {
  vi.mocked(api.createUpload).mockResolvedValue({ mediaId: "video", uploadUrl: "https://storage.test/put", requiredHeaders: { "If-None-Match": "*" } });
  vi.mocked(api.completeUpload).mockRejectedValueOnce(new Error("capacity")).mockResolvedValueOnce({ mediaId: "video", verifiedAt: "2026-10-17T00:00:00Z" });
  const upload = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
  vi.stubGlobal("fetch", upload);
  const run = createCatalogVideoUpload(file());
  await expect(run()).rejects.toThrow("capacity");
  await expect(run()).resolves.toMatchObject({ mediaId: "video" });
  expect(api.createUpload).toHaveBeenCalledOnce();
  expect(upload).toHaveBeenCalledOnce();
  expect(api.createUpload).toHaveBeenCalledWith({ purpose: "CATALOG_VIDEO", contentType: "video/mp4", fileSize: 11 });
  expect(api.completeUpload).toHaveBeenCalledTimes(2);
  expect(vi.mocked(api.completeUpload).mock.calls[0]).toEqual(vi.mocked(api.completeUpload).mock.calls[1]);
});

it("verifies an immutable object after an ambiguous PUT fails then returns 412 on retry", async () => {
  vi.mocked(api.createUpload).mockResolvedValue({ mediaId: "video", uploadUrl: "https://storage.test/put" });
  vi.mocked(api.completeUpload).mockResolvedValue({ mediaId: "video", verifiedAt: "2026-10-17T00:00:00Z" });
  const upload = vi.fn().mockRejectedValueOnce(new TypeError("network")).mockResolvedValueOnce(new Response(null, { status: 412 }));
  vi.stubGlobal("fetch", upload);
  const run = createCatalogVideoUpload(file());
  await expect(run()).rejects.toThrow("network");
  expect(api.completeUpload).not.toHaveBeenCalled();
  await expect(run()).resolves.toMatchObject({ verifiedAt: "2026-10-17T00:00:00Z" });
  expect(api.createUpload).toHaveBeenCalledOnce();
});

it("rejects unsupported, empty and over-50-MiB input before requesting any upload", () => {
  expect(() => validateCatalogVideoFile(new File(["x"], "clip.mov", { type: "video/quicktime" }))).toThrow();
  expect(() => validateCatalogVideoFile(new File([], "clip.mp4", { type: "video/mp4" }))).toThrow();
  const large = file(); Object.defineProperty(large, "size", { value: 50 * 1024 * 1024 + 1 });
  expect(() => createCatalogVideoUpload(large)).toThrow();
  expect(api.createUpload).not.toHaveBeenCalled();
});
