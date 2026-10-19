import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SocialPostContent } from "@/features/marketplace-view/social-post-content";
import type { components } from "@covie/api-client";

const media: components["schemas"]["SocialPreviewMedia"][] = [
  { id: "first", mimeType: "image/png", url: "https://media.test/first.png", sortOrder: 0, isCover: true },
  { id: "video", mimeType: "video/mp4", url: "http://localhost:3000/api/v1/media/video/content", sortOrder: 1,
    video: { width: 64, height: 48, durationMillis: 1000, codec: "h264" } },
  { id: "last", mimeType: "image/png", url: "https://media.test/last.png", sortOrder: 2 },
];
let intersection: IntersectionObserverCallback;
const disconnect = vi.fn();

beforeEach(() => {
  disconnect.mockClear();
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
  vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
  vi.stubGlobal("IntersectionObserver", class {
    constructor(callback: IntersectionObserverCallback) { intersection = callback; }
    observe() {}
    disconnect = disconnect;
  });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllEnvs(); vi.unstubAllGlobals(); disconnect.mockClear(); });

it("loads video only after explicit play without nesting native controls in the detail link", async () => {
  const view = render(<SocialPostContent media={media} name="Món hàng" href="/items/item" />);
  expect(view.container.querySelector("video")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Nội dung tiếp theo" }));
  const play = screen.getByRole("button", { name: "Phát video Món hàng" });
  expect(play.closest("a")).toBeNull();
  expect(view.container.querySelector("video")).toBeNull();
  expect(view.container.querySelector('img[src*="/content"]')).toBeNull();
  fireEvent.click(play);
  const video = view.container.querySelector("video")!;
  expect(video).not.toBeNull();
  expect(video).toHaveAttribute("src", media[1]!.url);
  expect(video).toHaveAttribute("preload", "none");
  expect(video.autoplay).toBe(false);
  expect(video.muted).toBe(true);
  expect(video.playsInline).toBe(true);
  expect(video.controls).toBe(true);
  expect(video.closest("a")).toBeNull();
  await waitFor(() => expect(video.play).toHaveBeenCalledOnce());
});

it("pauses out of view or on a hidden page and releases the source when changing slides", () => {
  const view = render(<SocialPostContent media={media.slice(1)} name="Món hàng" />);
  fireEvent.click(screen.getByRole("button", { name: "Phát video Món hàng" }));
  const video = view.container.querySelector("video")!;
  vi.mocked(video.pause).mockClear();
  act(() => intersection([{ isIntersecting: false } as IntersectionObserverEntry], {} as IntersectionObserver));
  expect(video.pause).toHaveBeenCalledOnce();
  vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
  fireEvent(document, new Event("visibilitychange"));
  expect(video.pause).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("button", { name: "Nội dung tiếp theo" }));
  expect(view.container.querySelector("video")).toBeNull();
  expect(video).not.toHaveAttribute("src");
  expect(video.load).toHaveBeenCalledOnce();
  expect(disconnect).toHaveBeenCalledOnce();
  expect(screen.getByAltText("Món hàng — Ảnh 2/2")).toBeInTheDocument();
});

it("fails to a finite accessible fallback and never retries playback automatically", () => {
  const view = render(<SocialPostContent media={[media[1]!]} name="Món hàng" />);
  fireEvent.click(screen.getByRole("button", { name: "Phát video Món hàng" }));
  fireEvent.error(view.container.querySelector("video")!);
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể phát video");
  expect(view.container.querySelector("video")).toBeNull();
  expect(screen.queryByRole("button", { name: "Phát video Món hàng" })).toBeNull();
  expect(HTMLMediaElement.prototype.play).toHaveBeenCalledOnce();
});

it("keeps video OFF by default without treating hidden video as an image", () => {
  vi.unstubAllEnvs();
  const view = render(<SocialPostContent media={media} name="Món hàng" />);
  expect(view.container.querySelector("video")).toBeNull();
  expect(screen.queryByRole("button", { name: "Phát video Món hàng" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Ảnh tiếp theo" }));
  expect(screen.getByAltText("Món hàng — Ảnh 2/2")).toHaveAttribute("src", media[2]!.url);
});

it("handles a rejected browser play promise without an unhandled rejection or automatic retry", async () => {
  vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new DOMException("Playback denied", "NotAllowedError"));
  const view = render(<SocialPostContent media={[media[1]!]} name="Món hàng" />);
  fireEvent.click(screen.getByRole("button", { name: "Phát video Món hàng" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể phát video");
  expect(view.container.querySelector("video")).toBeNull();
  expect(HTMLMediaElement.prototype.play).toHaveBeenCalledOnce();
});

it("pauses the previous card when another visible card starts playing", () => {
  const view = render(<>
    <SocialPostContent media={[media[1]!]} name="First card" />
    <SocialPostContent media={[media[1]!]} name="Second card" />
  </>);
  fireEvent.click(screen.getByRole("button", { name: "Phát video First card" }));
  const first = view.container.querySelector("video")!;
  fireEvent.play(first);
  vi.mocked(first.pause).mockClear();
  fireEvent.click(screen.getByRole("button", { name: "Phát video Second card" }));
  const second = view.container.querySelectorAll("video")[1]!;
  fireEvent.play(second);
  expect(first.pause).toHaveBeenCalledOnce();
});
