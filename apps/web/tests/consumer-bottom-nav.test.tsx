import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ConsumerBottomNav } from "@/components/layout/consumer-bottom-nav";

const navigation = vi.hoisted(() => ({ pathname: "/" }));

vi.mock("next/navigation", () => ({ usePathname: () => navigation.pathname }));

describe("ConsumerBottomNav", () => {
  beforeEach(() => { navigation.pathname = "/"; });

  it("stays hidden throughout checkout so it cannot cover the commit action", () => {
    navigation.pathname = "/checkout";
    const { rerender } = render(<ConsumerBottomNav />);
    expect(screen.queryByRole("navigation", { name: "Điều hướng chính" })).not.toBeInTheDocument();

    navigation.pathname = "/checkout/success/order-1";
    rerender(<ConsumerBottomNav />);
    expect(screen.queryByRole("navigation", { name: "Điều hướng chính" })).not.toBeInTheDocument();
  });

  it("stays hidden throughout address management to match the focused account flow", () => {
    navigation.pathname = "/me/addresses";
    const { rerender } = render(<ConsumerBottomNav />);
    expect(screen.queryByRole("navigation", { name: "Điều hướng chính" })).not.toBeInTheDocument();

    navigation.pathname = "/me/addresses/new";
    rerender(<ConsumerBottomNav />);
    expect(screen.queryByRole("navigation", { name: "Điều hướng chính" })).not.toBeInTheDocument();
  });

  it("remains available on ordinary consumer routes", () => {
    navigation.pathname = "/cart";
    render(<ConsumerBottomNav />);
    expect(screen.getByRole("navigation", { name: "Điều hướng chính" })).toBeInTheDocument();
  });

  it("keeps merchant public profiles in the Explore navigation context", () => {
    navigation.pathname = "/merchants/merchant-1";
    render(<ConsumerBottomNav />);

    expect(screen.getByRole("link", { name: "Khám phá" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Tôi" })).not.toHaveAttribute("aria-current");
  });
});
