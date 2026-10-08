import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { CatalogManagementList } from "@/features/catalog/catalog-management-list";

const merchantId = "33333333-3333-4333-8333-333333333333";
const items = [
  { id: "product-1", type: "PRODUCT" as const, listingKind: "STANDARD" as const, name: "Rau", listingStatus: "ACTIVE" as const, version: 0 },
  { id: "service-1", type: "SERVICE" as const, listingKind: "STANDARD" as const, name: "Sửa máy", listingStatus: "DRAFT" as const, version: 0 },
  { id: "resale-1", type: "PRODUCT" as const, listingKind: "RESALE" as const, name: "Bàn cũ", listingStatus: "PAUSED" as const, version: 0 },
];

afterEach(cleanup);

describe("seller catalog management list", () => {
  it("links each filtered row to its authoritative management detail route", () => {
    render(<CatalogManagementList merchantId={merchantId} items={items} />);
    expect(screen.getByRole("link", { name: /Rau/ })).toHaveAttribute("href", `/seller/${merchantId}/catalog/product-1`);

    fireEvent.click(screen.getByRole("button", { name: "Dịch vụ" }));
    expect(screen.getByRole("link", { name: /Sửa máy/ })).toHaveAttribute("href", `/seller/${merchantId}/catalog/service-1`);

    fireEvent.click(screen.getByRole("button", { name: "Thanh lý" }));
    expect(screen.getByRole("link", { name: /Bàn cũ/ })).toHaveAttribute("href", `/seller/${merchantId}/catalog/resale-1`);
  });

  it("keeps the truthful empty state for a selected item type", () => {
    render(<CatalogManagementList merchantId={merchantId} items={items.slice(0, 1)} />);
    fireEvent.click(screen.getByRole("button", { name: "Dịch vụ" }));
    expect(screen.getByText("Chưa có dịch vụ")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Thêm nội dung" })).toHaveLength(2);
    for (const link of screen.getAllByRole("link", { name: "Thêm nội dung" })) {
      expect(link).toHaveAttribute("href", `/seller/${merchantId}/catalog/new`);
    }
  });
});
