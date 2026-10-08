import { beforeEach, expect, it, vi } from "vitest";
import ReputationPage from "@/app/(buyer)/me/reputation/page";

const { requireUser } = vi.hoisted(() => ({ requireUser: vi.fn() }));
vi.mock("@/lib/auth/require-user", () => ({ requireUser }));

beforeEach(() => requireUser.mockReset());

it("keeps unauthenticated access under requireUser", async () => {
  requireUser.mockResolvedValue({ id: "buyer-1" });
  await ReputationPage();
  expect(requireUser).toHaveBeenCalledTimes(1);
});
