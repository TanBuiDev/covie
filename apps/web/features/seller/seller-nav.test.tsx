import type { AnchorHTMLAttributes, ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { SellerNavLinks } from "./seller-nav";

const mocks = vi.hoisted(() => ({
  pathname: "/seller/merchant-1/settings",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname,
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string; children: ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

const scrollIntoView = vi.fn();

beforeEach(() => {
  scrollIntoView.mockReset();
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
    configurable: true,
    value: scrollIntoView,
  });
  mocks.pathname = "/seller/merchant-1/settings";
});

it("keeps the frozen five-tab IA and scrolls the active mobile tab into view", () => {
  render(<SellerNavLinks merchantId="merchant-1" scrollActiveIntoView />);

  const links = screen.getAllByRole("link");
  expect(links.map((link) => link.textContent)).toEqual([
    "Tổng quan",
    "Đơn hàng",
    "Danh mục",
    "Điểm bán",
    "Cài đặt",
  ]);
  expect(screen.getByRole("link", { name: "Cài đặt" })).toHaveAttribute("aria-current", "page");
  expect(scrollIntoView).toHaveBeenCalledWith({ block: "nearest", inline: "nearest" });
});

it("does not force scroll behavior for the desktop/sidebar instance", () => {
  render(<SellerNavLinks merchantId="merchant-1" />);

  expect(screen.getByRole("link", { name: "Cài đặt" })).toHaveAttribute("aria-current", "page");
  expect(scrollIntoView).not.toHaveBeenCalled();
});

it("maps reputation and moderation notices to overview on mobile and settings on desktop", () => {
  for (const segment of ["reputation", "notices"]) {
    mocks.pathname = `/seller/merchant-1/${segment}`;
    render(<SellerNavLinks merchantId="merchant-1" scrollActiveIntoView />);
    expect(screen.getByRole("link", { name: "Tổng quan" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Cài đặt" })).not.toHaveAttribute("aria-current");
    cleanup();

    render(<SellerNavLinks merchantId="merchant-1" />);
    expect(screen.getByRole("link", { name: "Cài đặt" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Tổng quan" })).not.toHaveAttribute("aria-current");
    cleanup();
  }
});
