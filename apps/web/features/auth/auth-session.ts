import { readApiError } from "../../lib/api/api-error";
import { getCsrfToken, invalidateCsrfToken } from "./csrf";

let refreshPromise: Promise<void> | null = null;

export async function refreshSession(): Promise<void> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const csrf = await getCsrfToken();
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include",
      headers: { [csrf.headerName]: csrf.token },
    });
    if (!response.ok) throw await readApiError(response);
    invalidateCsrfToken();
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}
