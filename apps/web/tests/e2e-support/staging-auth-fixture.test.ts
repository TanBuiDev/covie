import { describe, expect, it, vi } from "vitest";
import { loginStagingRole, readStagingAuthConfig } from "../../e2e/fixtures/staging-auth-fixture";

const completeEnvironment = {
  COVIE_STAGING_BASE_URL: "https://staging.example.test",
  STAGING_BUYER_EMAIL: "buyer@example.test",
  STAGING_BUYER_PASSWORD: "buyer-password",
  STAGING_SELLER_EMAIL: "seller@example.test",
  STAGING_SELLER_PASSWORD: "seller-password",
  STAGING_ADMIN_EMAIL: "admin@example.test",
  STAGING_ADMIN_PASSWORD: "admin-password",
};

describe("staging auth fixture", () => {
  it("requires an HTTPS non-production target and all normal account credentials", () => {
    expect(readStagingAuthConfig(completeEnvironment)).toMatchObject({
      baseURL: "https://staging.example.test",
      buyer: { email: "buyer@example.test" },
      seller: { email: "seller@example.test" },
      admin: { email: "admin@example.test" },
    });

    expect(() => readStagingAuthConfig({ ...completeEnvironment, STAGING_ADMIN_PASSWORD: "" }))
      .toThrow("STAGING_ADMIN_PASSWORD");
    expect(() => readStagingAuthConfig({ ...completeEnvironment, COVIE_STAGING_BASE_URL: "http://staging.example.test" }))
      .toThrow("HTTPS");
    expect(() => readStagingAuthConfig({ ...completeEnvironment, COVIE_STAGING_BASE_URL: "https://covie.vn" }))
      .toThrow("production");
  });

  it("logs in through CSRF and the normal login endpoint only", async () => {
    const get = vi.fn().mockResolvedValue({
      ok: () => true,
      json: async () => ({ headerName: "X-CSRF-Token", token: "csrf-token" }),
    });
    const post = vi.fn().mockResolvedValue({ ok: () => true, status: () => 204, text: async () => "" });
    const goto = vi.fn().mockResolvedValue(undefined);
    const page = { request: { get, post }, goto };

    await loginStagingRole(page as never, "seller", readStagingAuthConfig(completeEnvironment));

    expect(get).toHaveBeenCalledWith("/api/v1/auth/csrf", { failOnStatusCode: false });
    expect(post).toHaveBeenCalledWith("/api/v1/auth/login", {
      data: { email: "seller@example.test", password: "seller-password" },
      failOnStatusCode: false,
      headers: { "X-CSRF-Token": "csrf-token" },
    });
    expect(post.mock.calls.flat().join(" ")).not.toContain("test-support");
    expect(goto).toHaveBeenCalledWith("/");
  });
});
