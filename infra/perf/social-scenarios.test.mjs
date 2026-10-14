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
} finally {
  if (previous === undefined) delete process.env.COVIE_SOCIAL_LOAD_ITEM_IDS;
  else process.env.COVIE_SOCIAL_LOAD_ITEM_IDS = previous;
}
