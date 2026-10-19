import { afterEach, expect, it, vi } from "vitest";
import { fetchSocialPreviews } from "@/features/marketplace-view/social-api";

const transport = vi.hoisted(() => ({ GET: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => transport, requestBrowserApi: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it("uses the generated public GET with a no-store request and does not request an empty batch", async () => {
  expect(await fetchSocialPreviews([])).toEqual({});
  expect(transport.GET).not.toHaveBeenCalled();
  transport.GET.mockResolvedValue({ data: { item: { itemId: "item", media: [] } }, response: new Response() });
  expect(await fetchSocialPreviews(["item", "item"])).toEqual({ item: { itemId: "item", media: [] } });
  expect(transport.GET).toHaveBeenCalledWith("/api/v1/catalog/social-previews", {
    params: { query: { itemIds: ["item"] } }, cache: "no-store",
  });
  await expect(fetchSocialPreviews(Array(41).fill("item"))).rejects.toThrow();
  expect(transport.GET).toHaveBeenCalledTimes(1);
});

it("preserves an API failure instead of fabricating an empty successful preview", async () => {
  const response = new Response(JSON.stringify({ error: { code: "RATE_LIMITED", message: "Retry" } }), {
    status: 429, headers: { "Content-Type": "application/json" },
  });
  const error = await response.json();
  transport.GET.mockResolvedValue({ response, error });
  await expect(fetchSocialPreviews(["item"])).rejects.toMatchObject({ status: 429, code: "RATE_LIMITED", message: "Retry" });
});
