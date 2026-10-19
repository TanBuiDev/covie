function socialIds() {
  const raw = process.env.COVIE_SOCIAL_LOAD_ITEM_IDS ?? "";
  const ids = raw.split(",").map((s) => s.trim()).filter(Boolean);
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (ids.length < 1 || ids.length > 40 || ids.some((id) => !uuid.test(id)) ||
      new Set(ids).size !== ids.length) {
    throw new Error("COVIE_SOCIAL_LOAD_ITEM_IDS requires 1–40 distinct UUIDs.");
  }
  return ids;
}

export const scenarios = {
  "public-search": {
    method: "GET",
    // Discovery requires a location context. Ho Chi Minh City is the pilot
    // baseline; override it when the deployment under test uses another area.
    path: `/api/v1/catalog/items?q=coffee&limit=20${process.env.COVIE_LOAD_LOCATION_QUERY ?? "&lat=10.7769&lng=106.7009"}`,
    expected: [200],
    p95Ms: 500,
    maxErrorRatio: 0.01,
  },
  "public-item-detail": {
    method: "GET",
    pathEnv: "COVIE_LOAD_ITEM_PATH",
    expected: [200],
    p95Ms: 300,
    maxErrorRatio: 0.01,
  },
  // Provisional Phase 4 targets; calibrate against production-like baseline before acceptance.
  "public-social-summary": {
    method: "GET",
    pathFactory: () => "/api/v1/catalog/social-summary?" +
      new URLSearchParams(socialIds().map((id) => ["itemIds", id])).toString(),
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
  },
  "public-social-comments": {
    method: "GET",
    pathFactory: () => "/api/v1/catalog/items/" + socialIds()[0] + "/comments?limit=5",
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
  },
  "public-social-previews": {
    method: "GET",
    pathFactory: () => "/api/v1/catalog/social-previews?" +
      new URLSearchParams(socialIds().map((itemId) => ["itemIds", itemId])).toString(),
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
  },
  "private-social-activity-liked": {
    method: "GET",
    path: "/api/v1/me/social/activity?type=LIKED&limit=20",
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
    authEnv: "COVIE_LOAD_AUTH_COOKIE",
  },
  "private-social-activity-saved": {
    method: "GET",
    path: "/api/v1/me/social/activity?type=SAVED&limit=20",
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
    authEnv: "COVIE_LOAD_AUTH_COOKIE",
  },
  "private-social-activity-commented": {
    method: "GET",
    path: "/api/v1/me/social/activity?type=COMMENTED&limit=20",
    expected: [200],
    p95Ms: 400,
    maxErrorRatio: 0.01,
    authEnv: "COVIE_LOAD_AUTH_COOKIE",
  },
  "order-list": {
    method: "GET",
    path: "/api/v1/me/orders?limit=20",
    expected: [200],
    p95Ms: 500,
    maxErrorRatio: 0.01,
    authEnv: "COVIE_LOAD_AUTH_COOKIE",
  },
};

export function resolveScenario(name) {
  const scenario = scenarios[name];
  if (!scenario) {
    throw new Error(`Unknown scenario '${name}'. Available: ${Object.keys(scenarios).join(", ")}`);
  }
  const path = scenario.pathFactory?.() ?? scenario.path ?? process.env[scenario.pathEnv];
  if (!path || !path.startsWith("/")) {
    throw new Error(`Scenario '${name}' requires ${scenario.pathEnv} to be an absolute API path`);
  }
  const authCookie = scenario.authEnv ? process.env[scenario.authEnv] : undefined;
  if (scenario.authEnv && !authCookie) {
    throw new Error(`Scenario '${name}' requires ${scenario.authEnv}`);
  }
  return { ...scenario, path, authCookie };
}
