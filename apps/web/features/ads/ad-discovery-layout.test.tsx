import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AdDiscoveryLayout } from "./ad-discovery-layout";
vi.mock("./ad-slot", () => ({ AdSlot: () => <aside aria-label="Quảng cáo bên trái" /> }));
afterEach(() => vi.restoreAllMocks());
it("preserves host content and shows no rail when the real gutter is too small", () => {
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({ left: 80, width: 1280 } as DOMRect);
  vi.stubGlobal("innerWidth", 1440);
  render(<AdDiscoveryLayout><h1>Nội dung sản phẩm</h1></AdDiscoveryLayout>);
  expect(screen.getByRole("heading")).toBeInTheDocument();
  expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
});
it("uses the available outer gutter when there is room for the 160px creative", async () => {
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({ left: 320, width: 1280 } as DOMRect);
  vi.stubGlobal("innerWidth", 1920);
  vi.stubGlobal("innerHeight", 1080);
  render(<AdDiscoveryLayout><h1>Nội dung sản phẩm</h1></AdDiscoveryLayout>);
  await waitFor(() => expect(screen.getByRole("complementary", { name: "Quảng cáo bên trái" })).toBeInTheDocument());
});
