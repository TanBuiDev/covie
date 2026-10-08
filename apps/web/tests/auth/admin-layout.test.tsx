import { beforeEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  requireUser: vi.fn(),
  notFound: vi.fn(() => { throw new Error("not found"); }),
}));

vi.mock("@/lib/auth/require-user", () => ({ requireUser: mocks.requireUser }));
vi.mock("next/navigation", () => ({ notFound: mocks.notFound }));
vi.mock("@/features/admin/admin-shell", () => ({
  AdminShell: ({ children }: { children: React.ReactNode }) => <div data-testid="admin-shell">{children}</div>,
}));

import AdminLayout from "../../app/(admin)/admin/layout";

beforeEach(() => {
  mocks.requireUser.mockReset();
  mocks.notFound.mockClear();
});

it("does not render the Admin shell for an authenticated non-admin", async () => {
  mocks.requireUser.mockResolvedValue({ globalRoles: ["USER"] });

  await expect(Promise.resolve(AdminLayout({ children: <p>Protected</p> }))).rejects.toThrow("not found");
  expect(mocks.notFound).toHaveBeenCalledOnce();
});

it("renders the Admin shell for an administrator", async () => {
  mocks.requireUser.mockResolvedValue({ globalRoles: ["USER", "ADMIN"] });

  const result = await AdminLayout({ children: <p>Protected</p> });

  expect(result.type).toBeDefined();
  expect(mocks.notFound).not.toHaveBeenCalled();
});
