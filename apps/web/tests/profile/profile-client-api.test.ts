import { beforeEach, expect, it, vi } from "vitest";
import { updateProfile } from "@/features/profile/profile-client-api";
const patch = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => ({ PATCH: patch }) }));
beforeEach(() => vi.clearAllMocks());
it("sends the generated profile request without changing login email", async () => {
  const user = { id: "buyer", displayName: "Tên mới" };
  patch.mockResolvedValue({ data: user, response: new Response(null, { status: 200 }) });
  expect(await updateProfile({ displayName: "Tên mới" })).toEqual(user);
  expect(patch).toHaveBeenCalledWith("/api/v1/me", { body: { displayName: "Tên mới" } });
});
it("does not confirm an incomplete successful profile response", async () => {
  patch.mockResolvedValue({ data: {}, response: new Response(null, { status: 200 }) });
  await expect(updateProfile({ displayName: "Tên mới" })).rejects.toThrow();
});
it("preserves structured unauthorized status for the editor", async () => {
  patch.mockResolvedValue({ error: { error: { code: "AUTHENTICATION_REQUIRED" } }, response: new Response(null, { status: 401 }) });
  await expect(updateProfile({ displayName: "Tên mới" })).rejects.toMatchObject({ status: 401 });
});
