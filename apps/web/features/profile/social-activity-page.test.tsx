import { afterEach, expect, it, vi } from "vitest";
import ActivityPage from "@/app/(buyer)/me/activity/page";
import MePage from "@/app/(buyer)/me/page";

const auth = vi.hoisted(() => ({ requireUser: vi.fn() }));
vi.mock("@/lib/auth/require-user", () => auth);
vi.mock("next/navigation", () => ({ notFound: () => { throw new Error("NOT_FOUND"); } }));
afterEach(() => { vi.unstubAllEnvs(); vi.resetAllMocks(); });

it("keeps the route and profile entry off by default without fetching private activity", async () => {
  vi.stubEnv("COVIE_SOCIAL_V2_ACTIVITY_ENABLED", "false");
  await expect(ActivityPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("NOT_FOUND");
  expect(auth.requireUser).not.toHaveBeenCalled();
  auth.requireUser.mockResolvedValue({ id: "buyer" });
  const page = await MePage();
  expect(page.props.children.props.activityAvailable).toBe(false);
});

it("passes trusted server identity and URL type and enables the matching profile entry", async () => {
  vi.stubEnv("COVIE_SOCIAL_V2_ACTIVITY_ENABLED", "true");
  auth.requireUser.mockResolvedValue({ id: "buyer" });
  const page = await ActivityPage({ searchParams: Promise.resolve({ type: "COMMENTED" }) });
  expect(auth.requireUser).toHaveBeenCalledWith("/me/activity?type=COMMENTED");
  expect(page.props.children.props).toEqual({ accountId: "buyer", type: "COMMENTED" });
  const profile = await MePage();
  expect(profile.props.children.props.activityAvailable).toBe(true);
  const fallback = await ActivityPage({ searchParams: Promise.resolve({ type: ["SAVED", "LIKED"] }) });
  expect(fallback.props.children.props.type).toBe("LIKED");
});
