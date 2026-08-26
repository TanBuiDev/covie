import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCatalogItemForServer } from "@/features/catalog/catalog-server-api";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api/server-api-client", () => ({
  createServerReadClient: async () => ({ GET: mocks.get }),
}));

describe("catalog server API", () => {
  beforeEach(() => mocks.get.mockReset());

  it("uses the absolute-base server client for the public item detail request", async () => {
    const data = { id: "item-1", name: "Rau củ theo mùa" };
    mocks.get.mockResolvedValue({ data, response: new Response(JSON.stringify(data), { status: 200 }) });

    await expect(getCatalogItemForServer("item-1", { kind: "coordinates", lat: 10.84, lng: 106.81 })).resolves.toBe(data);
    expect(mocks.get).toHaveBeenCalledWith("/api/v1/catalog/items/{itemId}", {
      params: {
        path: { itemId: "item-1" },
        query: { lat: 10.84, lng: 106.81 },
      },
    });
  });
});
