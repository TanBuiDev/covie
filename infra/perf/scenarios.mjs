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
  const path = scenario.path ?? process.env[scenario.pathEnv];
  if (!path || !path.startsWith("/")) {
    throw new Error(`Scenario '${name}' requires ${scenario.pathEnv} to be an absolute API path`);
  }
  const authCookie = scenario.authEnv ? process.env[scenario.authEnv] : undefined;
  if (scenario.authEnv && !authCookie) {
    throw new Error(`Scenario '${name}' requires ${scenario.authEnv}`);
  }
  return { ...scenario, path, authCookie };
}
