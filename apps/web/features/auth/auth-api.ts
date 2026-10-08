import { requestBrowserApi } from "../../lib/api/api-client";
import type { components } from "@covie/api-client";
import { readApiError } from "../../lib/api/api-error";
import { invalidateCsrfToken, getCsrfToken } from "./csrf";

export type LoginInput = { email: string; password: string };

async function post(path: string, body: unknown): Promise<void> {
  const response = await requestBrowserApi("POST", path, body);
  if (!response.ok) throw await readApiError(response);
  invalidateCsrfToken();
  await getCsrfToken();
}

export function login(input: LoginInput) {
  return post("/api/v1/auth/login", input);
}

export function setOnboardingPassword(password: string) {
  return post("/api/v1/auth/onboarding/set-password", { password });
}

export function resetPasswordWithGoogle(password: string) {
  const body: components["schemas"]["ResetPasswordWithGoogleRequest"] = { password };
  return post("/api/v1/auth/password/reset-with-google", body);
}

export async function logout(navigate: (path: string) => void): Promise<void> {
  const response = await requestBrowserApi("POST", "/api/v1/auth/logout");
  if (!response.ok) throw await readApiError(response);
  invalidateCsrfToken();
  navigate("/login");
}
