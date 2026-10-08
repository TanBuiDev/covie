import { render, screen, within } from "@testing-library/react";
import { expect, it } from "vitest";
import { SellerNavLinks } from "@/features/seller/seller-nav";
import { SellerShell } from "@/components/layout/seller-shell";
it("preserves the five mobile tabs and exposes ads outside the tab strip", () => {
  render(<SellerShell merchantId="merchant-1"><p>Content</p></SellerShell>);
  const header = screen.getByRole("banner");
  expect(within(header).getByRole("link", { name: "Quảng cáo" })).toHaveAttribute("href", "/seller/merchant-1/ads");
  const tabs = document.querySelector(".seller-mobile-tabs")!;
  expect(within(tabs as HTMLElement).getAllByRole("link")).toHaveLength(5);
  expect(within(tabs as HTMLElement).queryByRole("link", { name: "Quảng cáo" })).toBeNull();
});
it("keeps default SellerNavLinks frozen to five sections", () => {
  render(<SellerNavLinks merchantId="merchant-1" />);
  expect(screen.getAllByRole("link")).toHaveLength(5);
});
