import { afterEach, expect, it, vi } from "vitest";
import { resetPasswordWithGoogle, setOnboardingPassword } from "../../features/auth/auth-api";
import { invalidateCsrfToken } from "../../features/auth/csrf";

afterEach(() => {
  invalidateCsrfToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("sends the onboarding password in the backend onboarding payload", async () => {
  const fetchMock = successfulPostFetch();
  vi.stubGlobal("fetch", fetchMock);

  await setOnboardingPassword("a secure onboarding password");

  const request = fetchMock.mock.calls[1]![0] as Request;
  expect(request.url).toContain("/api/v1/auth/onboarding/set-password");
  await expect(request.clone().json()).resolves.toEqual({ password: "a secure onboarding password" });
});

it("sends the password field required by the Google reset API contract", async () => {
  const fetchMock = successfulPostFetch();
  vi.stubGlobal("fetch", fetchMock);

  await resetPasswordWithGoogle("a secure reset password");

  const request = fetchMock.mock.calls[1]![0] as Request;
  expect(request.url).toContain("/api/v1/auth/password/reset-with-google");
  await expect(request.clone().json()).resolves.toEqual({ password: "a secure reset password" });
});

function successfulPostFetch() {
  return vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "before" })))
    .mockResolvedValueOnce(new Response(null, { status: 200, headers: { "content-length": "0" } }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "after" })));
}
