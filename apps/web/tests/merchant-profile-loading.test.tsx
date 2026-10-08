import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import MerchantProfileLoading from "@/app/(public)/merchants/[merchantId]/loading";

describe("PUB-009 merchant profile loading", () => {
  it("matches the compact profile skeleton instead of rendering full-size content cards", () => {
    render(<MerchantProfileLoading />);

    const skeleton = screen.getByRole("region", { name: "Đang tải thông tin cửa hàng" });
    expect(skeleton.querySelectorAll('[data-skeleton="avatar"]')).toHaveLength(1);
    expect(skeleton.querySelectorAll('[data-skeleton="text"]')).toHaveLength(3);
    expect(skeleton.querySelectorAll('[data-skeleton="action"]')).toHaveLength(2);
    const header = screen.getByText("Cửa hàng").closest("header");
    expect(header?.querySelector("svg")).toBeInTheDocument();
    expect(header?.querySelector("img")).not.toBeInTheDocument();
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });
});
