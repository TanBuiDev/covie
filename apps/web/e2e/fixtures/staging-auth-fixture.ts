import type { Page } from "@playwright/test";

export type StagingRole = "buyer" | "seller" | "admin";

type Environment = Record<string, string | undefined>;
type Credentials = { email: string; password: string };

export type StagingAuthConfig = {
  baseURL: string;
  buyer: Credentials;
  seller: Credentials;
  admin: Credentials;
};

const credentialKeys = {
  buyer: ["STAGING_BUYER_EMAIL", "STAGING_BUYER_PASSWORD"],
  seller: ["STAGING_SELLER_EMAIL", "STAGING_SELLER_PASSWORD"],
  admin: ["STAGING_ADMIN_EMAIL", "STAGING_ADMIN_PASSWORD"],
} as const;

function required(environment: Environment, key: string): string {
  const value = environment[key]?.trim();
  if (!value) throw new Error(`${key} is required for the authenticated staging rehearsal.`);
  return value;
}

export function readStagingAuthConfig(environment: Environment = process.env): StagingAuthConfig {
  const baseURL = required(environment, "COVIE_STAGING_BASE_URL").replace(/\/$/, "");
  if (!baseURL.startsWith("https://")) throw new Error("COVIE_STAGING_BASE_URL must use HTTPS.");
  const hostname = new URL(baseURL).hostname.toLowerCase();
  if (hostname === "covie.vn" || hostname === "www.covie.vn") {
    throw new Error("Refusing to run the staging fixture against the production CoVie origin.");
  }

  const credentials = (role: StagingRole): Credentials => {
    const [emailKey, passwordKey] = credentialKeys[role];
    return { email: required(environment, emailKey), password: required(environment, passwordKey) };
  };

  return { baseURL, buyer: credentials("buyer"), seller: credentials("seller"), admin: credentials("admin") };
}

async function responseFailure(response: { status(): number; text(): Promise<string> }, operation: string) {
  await response.text();
  return new Error(`${operation} failed with HTTP ${response.status()}.`);
}

export async function loginStagingRole(
  page: Page,
  role: StagingRole,
  config: StagingAuthConfig = readStagingAuthConfig(),
): Promise<void> {
  const csrfResponse = await page.request.get("/api/v1/auth/csrf", { failOnStatusCode: false });
  if (!csrfResponse.ok()) throw await responseFailure(csrfResponse, "CSRF bootstrap");
  const csrf = await csrfResponse.json() as { headerName?: string; token?: string };
  if (!csrf.headerName || !csrf.token) throw new Error("CSRF bootstrap returned an incomplete token response.");

  const loginResponse = await page.request.post("/api/v1/auth/login", {
    data: config[role],
    failOnStatusCode: false,
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!loginResponse.ok()) throw await responseFailure(loginResponse, `${role} login`);

  // APIRequestContext is the page context's request object, so normal HttpOnly
  // session cookies are retained by the browser without exposing their values.
  await page.goto("/");
}

export function stagingAuthIsConfigured(environment: Environment = process.env): boolean {
  try {
    readStagingAuthConfig(environment);
    return true;
  } catch {
    return false;
  }
}
