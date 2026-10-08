import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MerchantProfilePage from "@/app/(public)/merchants/[merchantId]/page";

const api = vi.hoisted(() => ({
  getPublicMerchantForServer: vi.fn(),
  listPublicMerchantCatalogForServer: vi.fn(),
  listPublicMerchantReviewsForServer: vi.fn(),
}));

vi.mock("@/features/merchant/merchant-server-api", () => api);

describe("PUB-009 merchant profile page", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getPublicMerchantForServer.mockResolvedValue({
      id: "merchant-1",
      name: "Merchant identity survives",
      capabilities: { catalogTypes: ["PRODUCT"] },
    });
    api.listPublicMerchantReviewsForServer.mockResolvedValue(undefined);
    api.listPublicMerchantCatalogForServer.mockResolvedValue({ page: { items: [] } });
  });

  it("passes the URL cursor to the merchant-scoped catalog request", async () => {
    api.listPublicMerchantReviewsForServer.mockRejectedValue(new Error("reviews unavailable"));
    api.listPublicMerchantCatalogForServer.mockResolvedValue({
      page: { items: [{ id: "page-2-item", type: "PRODUCT", listingKind: "STANDARD", name: "Only page two" }] },
    });
    render(await MerchantProfilePage({
      params: Promise.resolve({ merchantId: "merchant-1" }),
      searchParams: Promise.resolve({ catalogCursor: "opaque cursor" }),
    }));

    expect(api.listPublicMerchantCatalogForServer).toHaveBeenCalledWith("merchant-1", "opaque cursor");
    expect(screen.getByRole("heading", { name: "Merchant identity survives" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Only page two/ })).toHaveAttribute("href", "/items/page-2-item");
    expect(screen.queryByText("Page one item")).not.toBeInTheDocument();
  });

  it("renders the merchant identity and a local catalog retry when only catalog fails", async () => {
    api.listPublicMerchantCatalogForServer.mockRejectedValue(new Error("catalog unavailable"));

    render(await MerchantProfilePage({
      params: Promise.resolve({ merchantId: "merchant-1" }),
      searchParams: Promise.resolve({ catalogCursor: "page-2" }),
    }));

    expect(screen.getByRole("heading", { name: "Merchant identity survives" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Thử lại danh mục" })).toHaveAttribute(
      "href",
      "/merchants/merchant-1?catalogCursor=page-2",
    );
  });
});
