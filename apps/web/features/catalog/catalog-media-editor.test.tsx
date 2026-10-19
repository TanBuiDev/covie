import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CatalogMediaEditor, useCatalogGallery } from "./catalog-media-editor";
import { createCatalogVideoUpload } from "@/features/media/direct-catalog-video-upload";
import { uploadImage } from "@/features/media/direct-image-upload";

vi.mock("./use-social-draft-navigation", () => ({ useSocialDraftNavigation: vi.fn() }));
vi.mock("@/features/media/direct-image-upload", async original => ({ ...await original<typeof import("@/features/media/direct-image-upload")>(), uploadImage: vi.fn() }));
vi.mock("@/features/media/direct-catalog-video-upload", async original => ({ ...await original<typeof import("@/features/media/direct-catalog-video-upload")>(), createCatalogVideoUpload: vi.fn() }));
const resolved = vi.fn();
const NativeURL = URL;
beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
  vi.stubGlobal("URL", class extends NativeURL {
    static override createObjectURL = vi.fn(() => "blob:preview");
    static override revokeObjectURL = vi.fn();
  });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
function Fixture() {
  const gallery = useCatalogGallery();
  return <><CatalogMediaEditor gallery={gallery} /><button onClick={() => { void gallery.resolveMediaIds().then(resolved).catch(() => {}); }}>Resolve</button></>;
}

it("retains mixed files on verification error and reuses the same upload job on explicit retry", async () => {
  const run = vi.fn().mockRejectedValueOnce(new Error("busy")).mockResolvedValueOnce({ mediaId: "video" });
  vi.mocked(createCatalogVideoUpload).mockReturnValue(run);
  vi.mocked(uploadImage).mockResolvedValue({ mediaId: "image" });
  render(<Fixture />);
  const video = new File(["video"], "clip.mp4", { type: "video/mp4" });
  const image = new File(["image"], "cover.png", { type: "image/png" });
  fireEvent.change(screen.getByLabelText("Ảnh và video Social"), { target: { files: [video, image] } });
  expect(screen.getByRole("button", { name: "Làm bìa clip.mp4" })).toBeDisabled();
  expect(screen.getByText("cover.png · Ảnh bìa")).toBeInTheDocument();
  expect(screen.queryByAltText("clip.mp4")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Resolve" }));
  await waitFor(() => expect(run).toHaveBeenCalledOnce());
  await screen.findByText("Lỗi tải video; lưu lại để thử lại");
  expect(screen.getByText("clip.mp4")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Resolve" }));
  await waitFor(() => expect(resolved).toHaveBeenCalledWith(["video", "image"]));
  expect(createCatalogVideoUpload).toHaveBeenCalledOnce();
  expect(uploadImage).toHaveBeenCalledOnce();
});

it("rejects a second video atomically and preserves an already selected gallery", () => {
  render(<Fixture />);
  const input = screen.getByLabelText("Ảnh và video Social");
  fireEvent.change(input, { target: { files: [new File(["a"], "a.mp4", { type: "video/mp4" })] } });
  fireEvent.change(input, { target: { files: [new File(["b"], "b.webm", { type: "video/webm" })] } });
  expect(screen.getByRole("alert")).toHaveTextContent("Tối đa 1 video");
  expect(screen.getByText("a.mp4")).toBeInTheDocument();
  expect(screen.queryByText("b.webm")).toBeNull();
});

it("keeps the existing image-only input and rejects video while the flag is OFF", async () => {
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "false");
  render(<Fixture />);
  const input = screen.getByLabelText("Ảnh Social");
  expect(input).not.toHaveAttribute("accept", expect.stringContaining("video"));
  await act(async () => fireEvent.change(input, { target: { files: [new File(["a"], "a.mp4", { type: "video/mp4" })] } }));
  expect(screen.getByRole("alert")).toBeInTheDocument();
  expect(createCatalogVideoUpload).not.toHaveBeenCalled();
});
