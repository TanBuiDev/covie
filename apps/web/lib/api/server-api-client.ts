import { headers } from "next/headers";
import { createApiClient } from "@/lib/api/api-client";
import { serverEnv } from "@/lib/env/server";

/** Creates a read-only API client for Server Components. */
export async function createServerReadClient() {
  const incoming = await headers();
  const cookie = incoming.get("cookie") ?? "";
  const client = createApiClient(serverEnv.API_INTERNAL_BASE_URL);
  const apiOrigin = new URL(serverEnv.API_INTERNAL_BASE_URL).origin;

  client.use({
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
