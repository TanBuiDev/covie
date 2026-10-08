import { beforeEach, describe, expect, it, vi } from "vitest";
import { listPublicMerchantCatalogForServer } from "./merchant-server-api";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api/server-api-client", () => ({
  createServerReadClient: async () => ({ GET: mocks.get }),
}));

describe("public merchant catalog server API", () => {
  beforeEach(() => mocks.get.mockReset());

  it("requests the three-card visual page while preserving an opaque cursor", async () => {
    const data = { page: { items: [], hasMore: false, nextCursor: null } };
    mocks.get.mockResolvedValue({ data, response: new Response(JSON.stringify(data), { status: 200 }) });

    await expect(listPublicMerchantCatalogForServer("merchant-1", "opaque+/=cursor")).resolves.toBe(data);
    expect(mocks.get).toHaveBeenCalledWith("/api/v1/catalog/merchants/{merchantId}/items", {
      params: {
        path: { merchantId: "merchant-1" },
        query: { limit: 3, cursor: "opaque+/=cursor" },
      },
    });
  });
});
