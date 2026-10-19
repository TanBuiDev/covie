import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdSlot } from "./ad-slot";
import { listActiveAds, trackAdEvent } from "./ad-api";

vi.mock("./ad-api", () => ({ listActiveAds: vi.fn(), trackAdEvent: vi.fn() }));
const first = { id: "ad-1", title: "Cà phê quanh bạn", body: "Khám phá gian hàng", ctaLabel: "Xem ngay", targetUrl: "/merchants/one", creativeType: "CSS", template: "NEARBY", placement: "IN_FEED", deliveryToken: "token-1", endsAt: "2099-01-01T00:00:00Z" };
let observe: (entries: Partial<IntersectionObserverEntry>[]) => void;
beforeEach(() => {
  vi.mocked(listActiveAds).mockResolvedValue([first] as never);
  vi.mocked(trackAdEvent).mockResolvedValue(undefined);
  vi.stubGlobal("matchMedia", vi.fn().mockImplementation(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  vi.stubGlobal("IntersectionObserver", class { constructor(callback: typeof observe) { observe = callback; } observe() {} disconnect() {} });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe("AdSlot", () => {
  it("renders approved creative copy and navigable target, with a disclosure", async () => {
    render(<AdSlot placement="IN_FEED" />);
    expect(await screen.findByRole("link", { name: "Cà phê quanh bạn — Xem ngay" })).toHaveAttribute("href", "/merchants/one");
    expect(screen.getByText("Quảng cáo")).toBeInTheDocument();
  });
  it("hides empty and failed ad reads without showing diagnostics", async () => {
    vi.mocked(listActiveAds).mockRejectedValue(new Error("private stack"));
    const { container } = render(<AdSlot placement="IN_FEED" />);
    await waitFor(() => expect(listActiveAds).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
  it("requires a continuous visible second and records once per delivery", async () => {
    render(<AdSlot placement="IN_FEED" />);
    await screen.findByRole("link");
    vi.useFakeTimers();
    act(() => observe([{ isIntersecting: true, intersectionRatio: 0.6 }]));
    await act(async () => { vi.advanceTimersByTime(700); });
    act(() => observe([{ isIntersecting: false, intersectionRatio: 0 }]));
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(trackAdEvent).not.toHaveBeenCalled();
    act(() => observe([{ isIntersecting: true, intersectionRatio: 0.6 }]));
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(trackAdEvent).toHaveBeenCalledTimes(1);
    expect(trackAdEvent).toHaveBeenCalledWith(expect.objectContaining({ id: "ad-1" }), "IMPRESSION", expect.any(String));
    await act(async () => { vi.advanceTimersByTime(2000); });
    expect(trackAdEvent).toHaveBeenCalledTimes(1);
  });
  it("sends click tracking without blocking link navigation", async () => {
    render(<AdSlot placement="IN_FEED" />);
    const link = await screen.findByRole("link");
    link.addEventListener("click", event => event.preventDefault());
    fireEvent.click(link);
    expect(trackAdEvent).toHaveBeenCalledWith(expect.objectContaining({ id: "ad-1" }), "CLICK", expect.any(String));
  });
  it("offers inside dots to switch between multiple campaigns", async () => {
    vi.mocked(listActiveAds).mockResolvedValue([first, { ...first, id: "ad-2", deliveryToken: "token-2", title: "Gian hàng nổi bật" }] as never);
    render(<AdSlot placement="IN_FEED" />);
    const dot2 = await screen.findByRole("tab", { name: "Chuyển đến quảng cáo 2" });
    fireEvent.click(dot2);
    expect(screen.getByRole("link", { name: "Gian hàng nổi bật — Xem ngay" })).toBeInTheDocument();
  });
  it("fails closed for unsafe destinations even if API data is invalid", async () => {
    vi.mocked(listActiveAds).mockResolvedValue([{ ...first, targetUrl: "javascript:alert(1)" }] as never);
    const { container } = render(<AdSlot placement="IN_FEED" />);
    await waitFor(() => expect(listActiveAds).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("removes a merchant link at delivery expiry without waiting for the next refresh", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-18T11:00:00Z"));
    vi.mocked(listActiveAds).mockResolvedValue([{ ...first, deliveryExpiresAt: "2026-10-18T11:00:01Z" }] as never);
    await act(async () => { render(<AdSlot placement="IN_FEED" />); });
    expect(screen.getByRole("link", { name: "Cà phê quanh bạn — Xem ngay" })).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(listActiveAds).toHaveBeenCalledTimes(1);
    expect(trackAdEvent).not.toHaveBeenCalled();
  });

  it("allows dismissing and restoring sidebar left rail banner", async () => {
    render(<AdSlot placement="SIDEBAR_LEFT" />);
    const dismissBtn = await screen.findByRole("button", { name: "Tạm ẩn quảng cáo" });
    fireEvent.click(dismissBtn);
    expect(screen.queryByRole("button", { name: "Tạm ẩn quảng cáo" })).not.toBeInTheDocument();
    const restoreBtn = screen.getByRole("button", { name: "Mở lại quảng cáo bên trái" });
    expect(restoreBtn).toBeInTheDocument();
    fireEvent.click(restoreBtn);
    expect(await screen.findByRole("button", { name: "Tạm ẩn quảng cáo" })).toBeInTheDocument();
  });
});
