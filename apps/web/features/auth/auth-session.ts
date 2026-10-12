import { readApiError } from "../../lib/api/api-error";
import { invalidateCsrfToken } from "./csrf";

let refreshPromise: Promise<void> | null = null;

export async function refreshSession(): Promise<void> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    // Share rotation with SSR; the same-origin bridge performs the backend CSRF exchange.
    const response = await fetch("/api/v1/auth/ssr-refresh?client=1", {
      credentials: "include",
      cache: "no-store",
    });
    if (!response.ok) throw await readApiError(response);
    invalidateCsrfToken();
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}
