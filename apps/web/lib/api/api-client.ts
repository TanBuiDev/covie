import { createCovieClient } from "@covie/api-client";
import { getCsrfToken, invalidateCsrfToken } from "../../features/auth/csrf";
import { readApiError } from "./api-error";
import { refreshSession } from "../../features/auth/auth-session";

export function createApiClient(baseUrl = "", observeResponse?: (response: Response) => void) {
  const client = createCovieClient(baseUrl || (typeof window === "undefined" ? "" : window.location.origin));
  const unsafe = new Set(["POST", "PUT", "PATCH", "DELETE"]);

  client.use({
    async onRequest({ request }) {
      if (unsafe.has(request.method.toUpperCase()) && !request.headers.has("Authorization")) {
        const csrf = await getCsrfToken();
        request.headers.set(csrf.headerName, csrf.token);
      }
      return request;
    },
    async onResponse({ response }) {
      if (response.status === 403) {
        const error = await readApiError(response.clone());
        if (error.code === "CSRF_INVALID") invalidateCsrfToken();
      }
      // Response middleware runs in reverse order: observe the final response after refresh/retry.
      observeResponse?.(response);
      return response;
    },
  });

  client.use({
    async onResponse({ request, response }) {
      if (typeof window === "undefined") return response;
      if (response.status !== 401) return response;

      const method = request.method.toUpperCase();
      const url = new URL(request.url, window.location.origin);
      if (url.pathname.startsWith("/api/v1/auth/")) return response;
      if (method !== "GET" && method !== "HEAD") return response;

      await refreshSession();
      return fetch(new Request(request.url, {
        method: request.method,
        headers: new Headers(request.headers),
        credentials: "include",
        cache: "no-store",
      }));
    },
  });

  return client;
}

type BrowserApiClient = {
  request(method: string, path: string, init?: {
    body?: unknown;
    headers?: HeadersInit;
    credentials?: RequestCredentials;
    fetch?: (request: Request) => Promise<Response>;
  }): Promise<{ response: Response }>;
};

/** Sends an ungenerated endpoint through the same CSRF-aware browser client. */
export async function requestBrowserApi(method: string, path: string, body?: unknown, headers?: HeadersInit): Promise<Response> {
  let rawResponse: Response | undefined;
  const client = createApiClient("", response => { rawResponse = response.clone(); }) as unknown as BrowserApiClient;
  await client.request(method, path, {
    body,
    headers,
    credentials: "include",
  });
  if (!rawResponse) throw new Error("Browser API request did not return a response");
  return rawResponse;
}
