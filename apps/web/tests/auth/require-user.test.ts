import { afterEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  redirect: vi.fn(() => { throw new Error("redirect"); }),
}));

vi.mock("@/lib/api/server-api-client", () => ({
  createServerReadClient: async () => ({ GET: mocks.get }),
}));
vi.mock("next/navigation", () => ({ redirect: mocks.redirect }));

import { requireUser } from "../../lib/auth/require-user";

afterEach(() => {
  mocks.get.mockReset();
  mocks.redirect.mockClear();
});

it.each([401, 403])("redirects an unauthenticated server read with status %i to login", async (status) => {
  mocks.get.mockResolvedValue({ data: undefined, response: new Response(null, { status }) });

  await expect(requireUser()).rejects.toThrow("redirect");
  expect(mocks.redirect).toHaveBeenCalledWith("/login");
});
