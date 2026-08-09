import { createCovieClient } from "@covie/api-client";
import { getCsrfToken, invalidateCsrfToken } from "../../features/auth/csrf";
import { readApiError } from "./api-error";
import { refreshSession } from "../../features/auth/auth-session";

export function createApiClient(baseUrl = "") {
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
      return response;
    },
  });

  client.use({
    async onResponse({ request, response }) {
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
    credentials?: RequestCredentials;
    fetch?: (request: Request) => Promise<Response>;
  }): Promise<{ response: Response }>;
};

/** Sends an ungenerated endpoint through the same CSRF-aware browser client. */
export async function requestBrowserApi(method: string, path: string, body?: unknown): Promise<Response> {
  const client = createApiClient() as unknown as BrowserApiClient;
  let rawResponse: Response | undefined;
  await client.request(method, path, {
    body,
    credentials: "include",
    fetch: async (request) => {
      rawResponse = await fetch(request);
      return rawResponse.clone();
    },
  });
  if (!rawResponse) throw new Error("Browser API request did not return a response");
  return rawResponse;
}
