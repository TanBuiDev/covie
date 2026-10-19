import { test } from "node:test";
import assert from "node:assert/strict";
import { resolveScenario } from "./scenarios.mjs";

const previous = process.env.COVIE_SOCIAL_LOAD_ITEM_IDS;
const a = "11111111-1111-4111-8111-111111111111";
const b = "22222222-2222-4222-8222-222222222222";
try {
  test("Phase 4 summary uses repeated itemIds query and a 40-ID cap", () => {
    process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = a + "," + b;
    const scenario = resolveScenario("public-social-summary");
    assert.equal(scenario.method, "GET");
    assert.deepEqual(new URLSearchParams(scenario.path.split("?")[1]).getAll("itemIds"), [a, b]);
    assert.equal(scenario.expected[0], 200);
  });
  test("Phase 4 comments checks a public listing with bounded pagination", () => {
    process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = a;
    assert.equal(resolveScenario("public-social-comments").path,
      "/api/v1/catalog/items/" + a + "/comments?limit=5");
  });
  test("Phase 4 rejects malformed, duplicate and oversized item sets", () => {
    for (const value of ["bad", a + "," + a, Array(41).fill(a).join(",")]) {
      process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = value;
      assert.throws(() => resolveScenario("public-social-summary"));
    }
  });
  test("V2 preview load uses the actual bounded repeated-ID read contract", () => {
    const ids = Array.from({ length: 40 }, (_, index) => "11111111-1111-4111-8111-" + String(index + 1).padStart(12, "0"));
    process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = ids.join(",");
    const scenario = resolveScenario("public-social-previews");
    assert.equal(scenario.method, "GET");
    assert.equal(scenario.path.split("?")[0], "/api/v1/catalog/social-previews");
    assert.deepEqual(new URLSearchParams(scenario.path.split("?")[1]).getAll("itemIds"), ids);
    assert.equal(scenario.authCookie, undefined);
    for (const value of ["bad", a + "," + a, [...ids, b].join(",")]) {
      process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = value;
      assert.throws(() => resolveScenario("public-social-previews"));
    }
  });
  test("V2 activity load requires a cookie and selects each actual type without userId", () => {
    const previousCookie = process.env.COVIE_LOAD_AUTH_COOKIE;
    try {
      delete process.env.COVIE_LOAD_AUTH_COOKIE;
      assert.throws(() => resolveScenario("private-social-activity-liked"), /requires COVIE_LOAD_AUTH_COOKIE/);
      process.env.COVIE_LOAD_AUTH_COOKIE = "covie_at=fixture-only-not-a-token";
      for (const type of ["LIKED", "SAVED", "COMMENTED"]) {
        const scenario = resolveScenario("private-social-activity-" + type.toLowerCase());
        assert.equal(scenario.method, "GET");
        assert.equal(scenario.path, "/api/v1/me/social/activity?type=" + type + "&limit=20");
        assert.equal(scenario.authCookie, process.env.COVIE_LOAD_AUTH_COOKIE);
        assert.equal(scenario.expected[0], 200);
      }
    } finally {
      if (previousCookie === undefined) delete process.env.COVIE_LOAD_AUTH_COOKIE;
      else process.env.COVIE_LOAD_AUTH_COOKIE = previousCookie;
    }
  });
} finally {
  if (previous === undefined) delete process.env.COVIE_SOCIAL_LOAD_ITEM_IDS;
  else process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = previous;
}
