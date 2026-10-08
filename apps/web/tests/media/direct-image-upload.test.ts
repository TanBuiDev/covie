import { afterEach, expect, it, vi } from "vitest";
import { uploadImage } from "../../features/media/direct-image-upload";

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("uploads bytes to the presigned URL and then completes the media", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf" }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({
      mediaId: "00000000-0000-0000-0000-000000000001",
      uploadUrl: "https://storage.example.test/put",
      requiredHeaders: { "Content-Type": "image/png", "If-None-Match": "*" },
    }), { status: 200, headers: { "Content-Type": "application/json" } }))
    .mockResolvedValueOnce(new Response(null, { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf-complete" }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ mediaId: "00000000-0000-0000-0000-000000000001", url: "https://cdn.test/a.png" }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("crypto", { randomUUID: () => "00000000-0000-0000-0000-000000000009" });
  const file = new File([new Uint8Array([137, 80, 78, 71])], "a.png", { type: "image/png" });

  await uploadImage(file, "CATALOG_IMAGE");

  expect(fetchMock.mock.calls[2]?.[0]).toBe("https://storage.example.test/put");
  expect(fetchMock.mock.calls[2]?.[1]).toMatchObject({ method: "PUT", body: file });
  expect(fetchMock.mock.calls[2]?.[1].headers).toMatchObject({ "If-None-Match": "*" });
  expect(fetchMock.mock.calls[3]?.[0]).toBe("/api/v1/auth/csrf");
  expect(fetchMock.mock.calls[4]?.[0].url).toContain("/api/v1/media/");
});

it("rejects unsupported or oversized files before creating an upload", async () => {
  const create = vi.fn();
  vi.stubGlobal("fetch", create);
  await expect(uploadImage(new File(["x"], "a.svg", { type: "image/svg+xml" }), "CATALOG_IMAGE")).rejects.toThrow("MEDIA_TYPE_NOT_SUPPORTED");
  expect(create).not.toHaveBeenCalled();
});
