import { expect, it } from "vitest";
import { safeReturnTo } from "../../lib/auth/return-to";

it.each([undefined, ["/me"], "https://evil.test", "//evil.test", "/\\evil.test", "/%2f%2fevil.test", "/me\n", "/api/v1/auth/logout", "/login", "/foo/../login"])("rejects an unsafe return destination %j", (value) => {
  expect(safeReturnTo(value)).toBe("/");
});

it("preserves a local destination and its selection", () => {
  expect(safeReturnTo("/checkout?source=DIRECT&quantity=2#review")).toBe("/checkout?source=DIRECT&quantity=2#review");
});
