import { expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ patch: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => ({ PATCH: mocks.patch, POST: mocks.post }) }));
import { runCatalogLifecycleAction, updateCatalogItem } from "@/features/catalog/catalog-api";

it.each(["edit", "pause"])("preserves BE version conflict for catalog %s stale handling", async (operation) => {
  const error = { error: { code: "RESOURCE_VERSION_CONFLICT", message: "Resource version has changed.", requestId: "catalog-conflict" } };
  const response = new Response(JSON.stringify(error), { status: 409 });
  await response.json();
  (operation === "edit" ? mocks.patch : mocks.post).mockResolvedValueOnce({ error, response });
  const request = operation === "edit"
    ? updateCatalogItem("merchant", "item", '"v1"', { name: "Updated" })
    : runCatalogLifecycleAction("merchant", "item", "PAUSE", '"v1"');
  await expect(request).rejects.toMatchObject({ status: 409, code: "RESOURCE_VERSION_CONFLICT", requestId: "catalog-conflict" });
});
