import { headers } from "next/headers";
import { createApiClient } from "@/lib/api/api-client";
import { serverEnv } from "@/lib/env/server";
import { redirect } from "next/navigation";
import { SSR_RETURN_HEADER, ssrRefreshUrl } from "@/lib/auth/ssr-refresh";

/** Creates a read-only API client for Server Components. */
export async function createServerReadClient() {
  const incoming = await headers();
  const cookie = incoming.get("cookie") ?? "";
  const client = createApiClient(serverEnv.API_INTERNAL_BASE_URL);
  const apiOrigin = new URL(serverEnv.API_INTERNAL_BASE_URL).origin;

  client.use({
    async onResponse({ request, response }) {
      if (response.status === 401 && new URL(request.url).origin === apiOrigin)
        redirect(ssrRefreshUrl(incoming.get(SSR_RETURN_HEADER) ?? undefined));
      return response;
    },
    async onRequest({ request }) {
      const forwarded = new Headers(request.headers);
      if (cookie && new URL(request.url).origin === apiOrigin) forwarded.set("cookie", cookie);
      return new Request(request, {
        headers: forwarded,
        cache: "no-store",
      });
    },
  });

  return client;
}
