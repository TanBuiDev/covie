import { afterEach, expect, it, vi } from "vitest";
import ExplorePage from "../../app/(public)/explore/page";

const mocks = vi.hoisted(() => ({ redirect: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: mocks.redirect }));

afterEach(() => { vi.clearAllMocks(); });

it("redirects the retired explore route to search", () => {
  ExplorePage();
  expect(mocks.redirect).toHaveBeenCalledWith("/search");
});
