import { render, screen } from "@testing-library/react";
import { useState } from "react";
import { beforeEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ detail: vi.fn(), categories: vi.fn() }));
vi.mock("@/features/catalog/catalog-server-api", () => ({
  getCatalogManagementDetailForServer: mocks.detail,
  listCategoriesForServer: mocks.categories,
}));
vi.mock("@/features/catalog/catalog-edit-form", () => ({
  CatalogEditForm: ({ item, etag }: { item: { name?: string; variants?: { version: number }[]; locationOffers?: { version: number }[] }; etag: string }) => {
    const [mountedEtag] = useState(etag);
    const [mountedVariantVersion] = useState(item.variants?.[0]?.version);
    const [mountedOfferVersion] = useState(item.locationOffers?.[0]?.version);
    return <div>{item.name} {mountedEtag} variant:{mountedVariantVersion} offer:{mountedOfferVersion}</div>;
  },
}));

beforeEach(() => {
  mocks.detail.mockReset();
  mocks.categories.mockReset();
});

it("loads the exact management aggregate and ETag for the seller edit route", async () => {
  mocks.detail.mockResolvedValue({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê" }, etag: '"v3"' });
  mocks.categories.mockResolvedValue([{ id: "category-1" }]);
  const Page = (await import("@/app/(seller)/seller/[merchantId]/catalog/[itemId]/page")).default;
  render(await Page({ params: Promise.resolve({ merchantId: "merchant-1", itemId: "item-1" }) }));
  expect(mocks.detail).toHaveBeenCalledWith("merchant-1", "item-1");
  expect(mocks.categories).toHaveBeenCalledWith("PRODUCT");
  expect(screen.getByText('Cà phê "v3" variant: offer:')).toBeInTheDocument();
});

it("remounts the editor after authoritative detail returns a new ETag", async () => {
  mocks.detail.mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê" }, etag: '"v3"' })
    .mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê mới" }, etag: '"v4"' });
  mocks.categories.mockResolvedValue([]);
  const Page = (await import("@/app/(seller)/seller/[merchantId]/catalog/[itemId]/page")).default;
  const params = Promise.resolve({ merchantId: "merchant-1", itemId: "item-1" });
  const { rerender } = render(await Page({ params }));
  rerender(await Page({ params }));
  expect(screen.getByText('Cà phê mới "v4" variant: offer:')).toBeInTheDocument();
});

it("remounts the editor after a variant changes independently of the item ETag", async () => {
  mocks.detail.mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê", variants: [{ id: "variant-1", version: 2 }] }, etag: '"v3"' })
    .mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê", variants: [{ id: "variant-1", version: 3 }] }, etag: '"v3"' });
  mocks.categories.mockResolvedValue([]);
  const Page = (await import("@/app/(seller)/seller/[merchantId]/catalog/[itemId]/page")).default;
  const params = Promise.resolve({ merchantId: "merchant-1", itemId: "item-1" });
  const { rerender } = render(await Page({ params }));
  rerender(await Page({ params }));
  expect(screen.getByText('Cà phê "v3" variant:3 offer:')).toBeInTheDocument();
});

it("remounts the editor after an offer changes independently of the item ETag", async () => {
  mocks.detail.mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê", locationOffers: [{ id: "offer-1", version: 4 }] }, etag: '"v3"' })
    .mockResolvedValueOnce({ item: { id: "item-1", type: "PRODUCT", name: "Cà phê", locationOffers: [{ id: "offer-1", version: 5 }] }, etag: '"v3"' });
  mocks.categories.mockResolvedValue([]);
  const Page = (await import("@/app/(seller)/seller/[merchantId]/catalog/[itemId]/page")).default;
  const params = Promise.resolve({ merchantId: "merchant-1", itemId: "item-1" });
  const { rerender } = render(await Page({ params }));
  rerender(await Page({ params }));
  expect(screen.getByText('Cà phê "v3" variant: offer:5')).toBeInTheDocument();
});
