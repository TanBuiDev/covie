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

it("preserves a protected destination when the session expires", async () => {
  mocks.get.mockResolvedValue({ response: new Response(null, { status: 401 }) });
  await expect(requireUser("/me")).rejects.toThrow("redirect");
  expect(mocks.redirect).toHaveBeenCalledWith("/login?returnTo=%2Fme");
});

it("keeps backend failures distinct from expired sessions", async () => {
  mocks.get.mockResolvedValue({ response: new Response(null, { status: 503 }) });
  await expect(requireUser()).rejects.toThrow("503");
  expect(mocks.redirect).not.toHaveBeenCalled();
});

afterEach(() => {
  mocks.get.mockReset();
  mocks.redirect.mockClear();
});

it.each([401, 403])("redirects an unauthenticated server read with status %i to login", async (status) => {
  mocks.get.mockResolvedValue({ data: undefined, response: new Response(null, { status }) });

  await expect(requireUser()).rejects.toThrow("redirect");
  expect(mocks.redirect).toHaveBeenCalledWith("/login");
});
