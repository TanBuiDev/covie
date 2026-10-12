import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AdSlot } from "./ad-slot";
import { listActiveAds, trackAdEvent } from "./ad-api";

vi.mock("./ad-api", () => ({ listActiveAds: vi.fn(), trackAdEvent: vi.fn() }));
const first = { id: "ad-1", title: "Campaign", ctaLabel: "View", targetUrl: "/search", creativeType: "CSS", template: "HERO", placement: "IN_FEED", deliveryToken: "token-1", endsAt: "2099-01-01T00:00:00Z", deliveryExpiresAt: "2099-01-01T00:00:00Z" };
let observe: (entries: Partial<IntersectionObserverEntry>[]) => void;
beforeEach(() => {
  vi.mocked(listActiveAds).mockResolvedValue([first] as never);
  vi.mocked(trackAdEvent).mockResolvedValue(undefined);
  vi.stubGlobal("matchMedia", vi.fn().mockImplementation(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  vi.stubGlobal("IntersectionObserver", class { constructor(callback: typeof observe) { observe = callback; } observe() {} disconnect() {} });
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.clearAllMocks(); Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" }); });

it("keeps video paused when the delivery first mounts in an already hidden tab", async () => {
  vi.mocked(listActiveAds).mockResolvedValue([{ ...first, creativeType: "VIDEO", mediaUrl: "https://media.test/ad.webm" }] as never);
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" });
  const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
  const { container } = render(<AdSlot placement="IN_FEED" />);
  await act(async () => {});
  expect(container.querySelector("video")).toBeInTheDocument();
  act(() => observe([{ isIntersecting: true, intersectionRatio: 1 }]));
  expect(play).not.toHaveBeenCalled();
});

it("restarts the continuous second after the tab becomes visible", async () => {
  render(<AdSlot placement="IN_FEED" />); await screen.findByRole("link"); vi.useFakeTimers();
  act(() => observe([{ isIntersecting: true, intersectionRatio: 1 }]));
  await act(async () => { vi.advanceTimersByTime(600); });
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" });
  fireEvent(document, new Event("visibilitychange"));
  await act(async () => { vi.advanceTimersByTime(5000); });
  expect(trackAdEvent).not.toHaveBeenCalled();
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" });
  fireEvent(document, new Event("visibilitychange"));
  await act(async () => { vi.advanceTimersByTime(999); });
  expect(trackAdEvent).not.toHaveBeenCalled();
  await act(async () => { vi.advanceTimersByTime(50); });
  expect(trackAdEvent).toHaveBeenCalledTimes(1);
});

it("does not serve or track an expired delivery despite a future campaign end", async () => {
  vi.mocked(listActiveAds).mockResolvedValue([{ ...first, deliveryExpiresAt: "2020-01-01T00:00:00Z" }] as never);
  const { container } = render(<AdSlot placement="IN_FEED" />);
  await act(async () => {});
  expect(container).toBeEmptyDOMElement();
  expect(trackAdEvent).not.toHaveBeenCalled();
});

it("does not count an image that failed during its visibility interval", async () => {
  vi.mocked(listActiveAds).mockResolvedValue([{ ...first, creativeType: "IMAGE", mediaUrl: "https://media.test/missing.png" }] as never);
  render(<AdSlot placement="IN_FEED" />); const image = await screen.findByRole("img"); vi.useFakeTimers();
  act(() => observe([{ isIntersecting: true, intersectionRatio: 1 }]));
  await act(async () => { vi.advanceTimersByTime(500); });
  fireEvent.error(image);
  await act(async () => { vi.advanceTimersByTime(500); });
  expect(trackAdEvent).not.toHaveBeenCalled();
});

it("does not replay a counted delivery after rotating away and back", async () => {
  vi.mocked(listActiveAds).mockResolvedValue([first, { ...first, id: "ad-2", deliveryToken: "token-2", title: "Second" }] as never);
  render(<AdSlot placement="IN_FEED" />); await screen.findByRole("link"); vi.useFakeTimers();
  act(() => observe([{ isIntersecting: true, intersectionRatio: 1 }]));
  await act(async () => { vi.advanceTimersByTime(1000); });
  fireEvent.click(screen.getByRole("tab", { name: "Chuyển đến quảng cáo 2" }));
  fireEvent.click(screen.getByRole("tab", { name: "Chuyển đến quảng cáo 1" }));
  act(() => observe([{ isIntersecting: true, intersectionRatio: 1 }]));
  await act(async () => { vi.advanceTimersByTime(1000); });
  expect(trackAdEvent).toHaveBeenCalledTimes(1);
});
